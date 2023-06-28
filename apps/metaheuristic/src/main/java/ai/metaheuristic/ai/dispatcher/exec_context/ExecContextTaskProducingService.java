/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.event.events.FindUnassignedTasksAndRegisterInQueueTxEvent;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionRegisterService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationService;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 10/3/2020
 * Time: 8:05 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextTaskProducingService {

    private final TaskProducingService taskProducingService;
    private final ExecContextCache execContextCache;
    private final SourceCodeValidationService sourceCodeValidationService;
    private final ApplicationEventPublisher eventPublisher;

    public SourceCodeApiData.TaskProducingResultComplex produceAndStartAllTasks(
            SourceCodeImpl sourceCode, ExecContextImpl execContext) {
        TxUtils.checkTxExists();
        ExecContextSyncService.checkWriteLockPresent(execContext.id);

        SourceCodeApiData.TaskProducingResultComplex result = new SourceCodeApiData.TaskProducingResultComplex();
        long mills = System.currentTimeMillis();
        result.sourceCodeValidationResult = sourceCodeValidationService.checkConsistencyOfSourceCode(sourceCode);
        log.info("#701.100 SourceCode {} was validated for {} ms.", sourceCode.uid, System.currentTimeMillis() - mills);

        if (result.sourceCodeValidationResult.status != EnumsApi.SourceCodeValidateStatus.OK) {
            log.error("#701.120 Can't produce tasks, error: {}", result.sourceCodeValidationResult);
            execContext.setState(EnumsApi.ExecContextState.STOPPED.code);
            return result;
        }
        mills = System.currentTimeMillis();

        log.info("#701.140 Start producing tasks for SourceCode {}, execContextId: #{}", sourceCode.uid, execContext.id);

        // create all not dynamic tasks
        TaskData.ProduceTaskResult produceTaskResult = produceTasksForExecContext(execContext);
        if (produceTaskResult.status== EnumsApi.TaskProducingStatus.OK) {
            log.info("#701.160 Tasks were produced with status {}", produceTaskResult.status);
        }
        else {
            log.info("#701.180 Tasks were produced with status {}, error: {}", produceTaskResult.status, produceTaskResult.error);
        }

        if (produceTaskResult.status==EnumsApi.TaskProducingStatus.OK) {
            execContext.state = EnumsApi.ExecContextState.STARTED.code;
        }
        else {
            execContext.state = EnumsApi.ExecContextState.ERROR.code;
        }
        execContextCache.save(execContext);

        result.sourceCodeValidationResult = ConstsApi.SOURCE_CODE_VALIDATION_RESULT_OK;
        result.taskProducingStatus = produceTaskResult.status;

        log.info("#701.190 SourceCodeService.produceTasks('{}') was processed for {} ms.", sourceCode.uid, System.currentTimeMillis() - mills);
        eventPublisher.publishEvent(new FindUnassignedTasksAndRegisterInQueueTxEvent());

        return result;
    }

    private TaskData.ProduceTaskResult produceTasksForExecContext(ExecContextImpl execContext) {
        final ExecContextParamsYaml execContextParamsYaml = execContext.getExecContextParamsYaml();
        DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph = ExecContextProcessGraphService.importProcessGraph(execContextParamsYaml);

        TaskData.ProduceTaskResult okResult = new TaskData.ProduceTaskResult(EnumsApi.TaskProducingStatus.OK, null);
        Map<String, List<Long>> parentProcesses = new HashMap<>();
        for (ExecContextData.ProcessVertex processVertex : processGraph) {
            String processCode = processVertex.process;
            ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(processCode);
            if (p == null) {
                // mh.finish can be omitted in sourceCode
                if (processCode.equals(Consts.MH_FINISH_FUNCTION)) {
                    p = new ExecContextParamsYaml.Process(Consts.MH_FINISH_FUNCTION, Consts.MH_FINISH_FUNCTION, Consts.TOP_LEVEL_CONTEXT_ID,
                            Consts.MH_FINISH_FUNCTION_INSTANCE);
                }
                else {
                    return new TaskData.ProduceTaskResult(EnumsApi.TaskProducingStatus.PROCESS_NOT_FOUND_ERROR, "#701.200 Process '"+processCode+"' wasn't found");
                }
            }
            if (InternalFunctionRegisterService.getInternalFunction(p.function.code)!=null && p.function.context!=EnumsApi.FunctionExecContext.internal) {
                return new TaskData.ProduceTaskResult(EnumsApi.TaskProducingStatus.INTERNAL_FUNCTION_DECLARED_AS_EXTERNAL_ERROR,
                        "#701.220 Process '"+processCode+"' must be internal");
            }
            ExecContextParamsYaml.Process internalFuncProcess = checkForInternalFunctionAsParent(execContextParamsYaml, processGraph, processVertex);
            // internal functions will be processed in another thread
            if (internalFuncProcess!=null) {
                continue;
            }

            List<Long> parentTaskIds = new ArrayList<>();
            processGraph.incomingEdgesOf(processVertex).stream()
                    .map(processGraph::getEdgeSource)
                    .map(ancestor -> parentProcesses.get(ancestor.process))
                    .filter(Objects::nonNull)
                    .forEach(parentTaskIds::addAll);

            final ExecContextParamsYaml.Process process = p;
            TaskData.ProduceTaskResult result =
                    taskProducingService.produceTaskForProcess(
                            process, execContextParamsYaml, execContext.id,
                            execContext.execContextGraphId, execContext.execContextTaskStateId, parentTaskIds);

            if (result.status!= EnumsApi.TaskProducingStatus.OK) {
                return result;
            }

            parentProcesses.computeIfAbsent(p.processCode, o->new ArrayList<>()).add(result.taskId);
        }
        return okResult;
    }

    // the logic is following: because we goes through all processed, we have to filter out any processes whose ancestor is internal task
    // there is a trick - we have to stop scanning when we've reached the top-level process, i.e. internalContextId=="1"
    @Nullable
    public static ExecContextParamsYaml.Process checkForInternalFunctionAsParent(ExecContextParamsYaml execContextParamsYaml, DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph, ExecContextData.ProcessVertex currProcess) {
        if (currProcess.processContextId.equals(Consts.TOP_LEVEL_CONTEXT_ID)) {
            return null;
        }

        ExecContextData.ProcessVertex directAncestor = currProcess;
        while ((directAncestor=getDirectAncestor(processGraph, directAncestor))!=null) {
            ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(directAncestor.process);
            if (p==null) {
                log.warn("#701.260 Unusual state, need to investigate");
                continue;
            }
            if (p.function.context== EnumsApi.FunctionExecContext.internal) {
                return p;
            }

        }
        return null;
    }

    @SuppressWarnings("SimplifyStreamApiCallChains")
    @Nullable
    private static ExecContextData.ProcessVertex getDirectAncestor(DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph, ExecContextData.ProcessVertex currProcess) {
        if (currProcess.processContextId.equals(Consts.TOP_LEVEL_CONTEXT_ID)) {
            return null;
        }
        List<ExecContextData.ProcessVertex> l = ExecContextProcessGraphService.findDirectAncestors(processGraph, currProcess).stream()
                .filter(o->currProcess.processContextId.startsWith(o.processContextId))
                .collect(Collectors.toList());

        if (l.isEmpty()) {
            return null;
        }
        if (l.size()!=1) {
            throw new IllegalStateException("(l.size()!=1)");
        }

        return l.get(0);
    }


}

/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.*;

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
    private final SourceCodeValidationService sourceCodeValidationService;

    public SourceCodeApiData.TaskProducingResultComplex produceAndStartAllTasks(
            SourceCodeImpl sourceCode, ExecContextImpl execContext, ExecContextParamsYaml execContextParamsYaml) {
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
        TaskData.ProduceTaskResult produceTaskResult = produceTasksForExecContext(execContext, execContextParamsYaml);
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
        result.sourceCodeValidationResult = ConstsApi.SOURCE_CODE_VALIDATION_RESULT_OK;
        result.taskProducingStatus = produceTaskResult.status;

        log.info("#701.140 SourceCodeService.produceTasks('{}') was processed for {} ms.", sourceCode.uid, System.currentTimeMillis() - mills);

        return result;
    }

    private TaskData.ProduceTaskResult produceTasksForExecContext(ExecContextImpl execContext, ExecContextParamsYaml execContextParamsYaml) {
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
            if (InternalFunctionRegisterService.isRegistered(p.function.code) && p.function.context!= EnumsApi.FunctionExecContext.internal) {
                return new TaskData.ProduceTaskResult(EnumsApi.TaskProducingStatus.INTERNAL_FUNCTION_DECLARED_AS_EXTERNAL_ERROR,
                        "#701.220 Process '"+processCode+"' must be internal");
            }
            Set<ExecContextData.ProcessVertex> ancestors = ExecContextProcessGraphService.findAncestors(processGraph, processVertex);
            ExecContextParamsYaml.Process internalFuncProcess = checkForInternalFunctions(execContextParamsYaml, ancestors, p);

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

    @Nullable
    private static ExecContextParamsYaml.Process checkForInternalFunctions(ExecContextParamsYaml execContextParamsYaml, Set<ExecContextData.ProcessVertex> ancestors, ExecContextParamsYaml.Process currProcess) {
        for (ExecContextData.ProcessVertex ancestor : ancestors) {
            ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(ancestor.process);
            if (p==null) {
                log.warn("#701.260 Unusual state, need to investigate");
                continue;
            }
            if (!currProcess.internalContextId.startsWith(p.internalContextId)) {
                continue;
            }
            if (p.internalContextId.equals(currProcess.internalContextId)) {
                continue;
            }
            if (p.function.context== EnumsApi.FunctionExecContext.internal) {
                return p;
            }
        }
        return null;
    }


}

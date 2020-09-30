/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Monitoring;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextProcessGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionRegisterService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationService;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static ai.metaheuristic.api.EnumsApi.FunctionExecContext;
import static ai.metaheuristic.api.EnumsApi.TaskProducingStatus;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskProducingService {

    private final ExecContextSyncService execContextSyncService;
    private final ExecContextFSM execContextFSM;
    private final TaskTransactionalService taskTransactionalService;

    private final ExecContextRepository execContextRepository;
    private final SourceCodeCache sourceCodeCache;
    private final SourceCodeValidationService sourceCodeValidationService;
    private final InternalFunctionRegisterService internalFunctionRegisterService;

    // TODO 2019.05.19 add reporting of producing of tasks
    // TODO 2020.01.17 reporting to where? do we need to implement it?
    // TODO 2020.09.28 reporting is about dynamically inform a web application about the current status of creating
    @Transactional
    public synchronized void createAllTasks() {

        Monitoring.log("##019", Enums.Monitor.MEMORY);
        List<ExecContextImpl> execContexts = execContextRepository.findByState(EnumsApi.ExecContextState.PRODUCING.code);
        Monitoring.log("##020", Enums.Monitor.MEMORY);
        if (!execContexts.isEmpty()) {
            log.info("#701.020 Start producing tasks");
        }
        for (ExecContextImpl execContext : execContexts) {
            SourceCodeImpl sourceCode = sourceCodeCache.findById(execContext.getSourceCodeId());
            if (sourceCode==null) {
                execContextFSM.toStopped(execContext.id);
                continue;
            }
            Monitoring.log("##021", Enums.Monitor.MEMORY);
            log.info("#701.030 Producing tasks for sourceCode.code: {}, input resource pool: \n{}",sourceCode.uid, execContext.getParams());
            produceAllTasks(true, sourceCode, execContext);
            Monitoring.log("##022", Enums.Monitor.MEMORY);
        }
        if (!execContexts.isEmpty()) {
            log.info("#701.040 Producing of tasks was finished");
        }
    }

    public SourceCodeApiData.TaskProducingResultComplex produceAllTasks(boolean isPersist, SourceCodeImpl sourceCode, ExecContextImpl execContext) {
        SourceCodeApiData.TaskProducingResultComplex result = new SourceCodeApiData.TaskProducingResultComplex();
        if (isPersist && execContext.getState()!= EnumsApi.ExecContextState.PRODUCING.code) {
            result.sourceCodeValidationResult = new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.ALREADY_PRODUCED_ERROR, "Tasks were produced already");
            return result;
        }
        long mills = System.currentTimeMillis();
        result.sourceCodeValidationResult = sourceCodeValidationService.checkConsistencyOfSourceCode(sourceCode);
        log.info("#701.150 SourceCode was validated for "+(System.currentTimeMillis() - mills) + " ms.");
        if (result.sourceCodeValidationResult.status != EnumsApi.SourceCodeValidateStatus.OK &&
                result.sourceCodeValidationResult.status != EnumsApi.SourceCodeValidateStatus.EXPERIMENT_ALREADY_STARTED_ERROR ) {
            log.error("#701.160 Can't produce tasks, error: {}", result.sourceCodeValidationResult);
            if(isPersist) {
                execContextFSM.toStopped(execContext.getId());
            }
            return result;
        }
        Monitoring.log("##022", Enums.Monitor.MEMORY);
        mills = System.currentTimeMillis();
        result = produceTasks(isPersist, execContext);
        log.info("#701.170 SourceCodeService.produceTasks() was processed for "+(System.currentTimeMillis() - mills) + " ms.");
        Monitoring.log("##033", Enums.Monitor.MEMORY);

        return result;
    }

    @Transactional
    public SourceCodeApiData.TaskProducingResultComplex produceTasks(boolean isPersist, ExecContextImpl execContext) {
        return execContextSyncService.getWithSync(execContext.id, () -> {

            ExecContextParamsYaml execContextParamsYaml = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(execContext.params);

            // create all not dynamic tasks
            TaskData.ProduceTaskResult produceTaskResult = produceTasks(isPersist, execContext.sourceCodeId, execContext.id, execContextParamsYaml);
            if (produceTaskResult.status== EnumsApi.TaskProducingStatus.OK) {
                log.info(S.f("#705.560 Tasks were produced with status %s", produceTaskResult.status));
            }
            else {
                log.info(S.f("#705.580 Tasks were produced with status %s, error: %s", produceTaskResult.status, produceTaskResult.error));
            }


            SourceCodeApiData.TaskProducingResultComplex result = new SourceCodeApiData.TaskProducingResultComplex();
            if (isPersist) {
                if (produceTaskResult.status== EnumsApi.TaskProducingStatus.OK) {
                    execContextFSM.toProduced(execContext.id);
                }
                else {
                    execContextFSM.toError(execContext.id);
                }
            }
            result.numberOfTasks = produceTaskResult.numberOfTasks;
            result.sourceCodeValidationResult = ConstsApi.SOURCE_CODE_VALIDATION_RESULT_OK;
            result.taskProducingStatus = produceTaskResult.status;

            return result;
        });
    }

    private TaskData.ProduceTaskResult produceTasks(boolean isPersist, Long sourceCodeId, Long execContextId, ExecContextParamsYaml execContextParamsYaml) {
        DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph = ExecContextProcessGraphService.importProcessGraph(execContextParamsYaml);

        TaskData.ProduceTaskResult okResult = new TaskData.ProduceTaskResult(TaskProducingStatus.OK, null);
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
                    return new TaskData.ProduceTaskResult(TaskProducingStatus.PROCESS_NOT_FOUND_ERROR, "#375.020 Process '"+processCode+"' wasn't found");
                }
            }
            if (internalFunctionRegisterService.isRegistered(p.function.code) && p.function.context!= FunctionExecContext.internal) {
                return new TaskData.ProduceTaskResult(TaskProducingStatus.INTERNAL_FUNCTION_DECLARED_AS_EXTERNAL_ERROR,
                        "#375.040 Process '"+processCode+"' must be internal");
            }
            Set<ExecContextData.ProcessVertex> ancestors = ExecContextProcessGraphService.findAncestors(processGraph, processVertex);
            ExecContextParamsYaml.Process internalFuncProcess = checkForInternalFunctions(execContextParamsYaml, ancestors, p);

            if (internalFuncProcess!=null) {
                log.info(S.f("There is ancestor which is internal function: %s, process: %s", internalFuncProcess.function.code, internalFuncProcess.processCode));
                continue;
            }


            List<Long> parentTaskIds = new ArrayList<>();
            processGraph.incomingEdgesOf(processVertex).stream()
                    .map(processGraph::getEdgeSource)
                    .map(ancestor -> parentProcesses.get(ancestor.process))
                    .filter(Objects::nonNull)
                    .forEach(parentTaskIds::addAll);

            TaskData.ProduceTaskResult result = taskTransactionalService.produceTaskForProcess(isPersist, sourceCodeId, p, execContextParamsYaml, execContextId, parentTaskIds);
            if (result.status!= TaskProducingStatus.OK) {
                return result;
            }
            parentProcesses.computeIfAbsent(p.processCode, o->new ArrayList<>()).add(result.taskId);
            okResult.numberOfTasks += result.numberOfTasks;
        }
        return okResult;
    }

    @Nullable
    private ExecContextParamsYaml.Process checkForInternalFunctions(ExecContextParamsYaml execContextParamsYaml, Set<ExecContextData.ProcessVertex> ancestors, ExecContextParamsYaml.Process currProcess) {
        for (ExecContextData.ProcessVertex ancestor : ancestors) {
            ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(ancestor.process);
            if (p==null) {
                log.warn("#375.060 Unusual state, need to investigate");
                continue;
            }
            if (!currProcess.internalContextId.startsWith(p.internalContextId)) {
                continue;
            }
            if (p.internalContextId.equals(currProcess.internalContextId)) {
                continue;
            }
            if (p.function.context== FunctionExecContext.internal) {
                return p;
            }
        }
        return null;
    }

}

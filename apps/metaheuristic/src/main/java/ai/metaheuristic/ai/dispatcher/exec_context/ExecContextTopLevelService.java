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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.event.ReconcileStatesEvent;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 7/4/2019
 * Time: 3:56 PM
 */
@SuppressWarnings("DuplicatedCode")
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class ExecContextTopLevelService {

    private final ExecContextService execContextService;
    private final ExecContextRepository execContextRepository;
    private final ExecContextSyncService execContextSyncService;
    private final ExecContextFSM execContextFSM;
    private final ExecContextTaskProducingService execContextTaskProducingService;
    private final TaskRepository taskRepository;
    private final ExecContextTaskAssigningService execContextTaskAssigningService;

    public ExecContextApiData.ExecContextStateResult getExecContextState(Long sourceCodeId, Long execContextId, DispatcherContext context) {
        return execContextSyncService.getWithSync(execContextId, ()-> execContextService.getExecContextState(sourceCodeId, execContextId, context));
    }

    public List<Long> storeAllConsoleResults(List<ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult> results) {
        List<Long> ids = new ArrayList<>();
        for (ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result : results) {
            ids.add(result.taskId);
            storeExecResult(result);
        }
        return ids;
    }

    public SourceCodeApiData.ExecContextResult getExecContextExtended(Long execContextId) {
        SourceCodeApiData.ExecContextResult result = execContextSyncService.getWithSync(execContextId,
                ()-> execContextService.getExecContextExtended(execContextId));

        if (result.isErrorMessages()) {
            return result;
        }

        if (!result.sourceCode.getId().equals(result.execContext.getSourceCodeId())) {
            execContextSyncService.getWithSyncNullable(execContextId,
                    ()-> execContextService.changeValidStatus(execContextId, false));
            return new SourceCodeApiData.ExecContextResult("#705.220 sourceCodeId doesn't match to execContext.sourceCodeId, " +
                    "sourceCodeId: " + result.execContext.getSourceCodeId() + ", execContext.sourceCodeId: " + result.execContext.getSourceCodeId());
        }
        return result;
    }


    @Nullable
    public DispatcherCommParamsYaml.AssignedTask findTaskInExecContext(ProcessorCommParamsYaml.ReportProcessorTaskStatus reportProcessorTaskStatus, Long processorId, boolean isAcceptOnlySigned) {
        List<Long> execContextIds = execContextRepository.findAllStartedIds();
        for (Long execContextId : execContextIds) {
            DispatcherCommParamsYaml.AssignedTask assignedTask = findTaskInExecContext(reportProcessorTaskStatus, processorId, isAcceptOnlySigned, execContextId);
            if (assignedTask != null) {
                return assignedTask;
            }
        }
        return null;
    }

    public void findUnassignedInternalTaskAndAssign() {
        List<Long> execContextIds = execContextRepository.findAllStartedIds();
        for (Long execContextId : execContextIds) {
            VariableData.DataStreamHolder holder = new VariableData.DataStreamHolder();
            try {
                execContextSyncService.getWithSyncNullable(execContextId,
                        ()->execContextTaskAssigningService.findUnassignedInternalTaskAndAssign(execContextId, holder));
            }
            finally {
                for (InputStream inputStream : holder.inputStreams) {
                    try {
                        inputStream.close();
                    }
                    catch(Throwable th)  {
                        log.warn("Error while closing stream", th);
                    }
                }
            }
        }
    }

    @Nullable
    public DispatcherCommParamsYaml.AssignedTask findTaskInExecContext(ProcessorCommParamsYaml.ReportProcessorTaskStatus reportProcessorTaskStatus, Long processorId, boolean isAcceptOnlySigned, Long execContextId) {
        DispatcherCommParamsYaml.AssignedTask assignedTask = execContextSyncService.getWithSync(execContextId,
                ()-> execContextFSM.getTaskAndAssignToProcessor(reportProcessorTaskStatus, processorId, isAcceptOnlySigned, execContextId));

        if (assignedTask!=null && log.isDebugEnabled()) {
            TaskImpl task = taskRepository.findById(assignedTask.taskId).orElse(null);
            if (task==null) {
                log.debug("#705.075 findTaskInExecContext(), task #{} wasn't found", assignedTask.taskId);
            }
            else {
                log.debug("#705.078 findTaskInExecContext(), task id: #{}, ver: {}, task: {}", task.id, task.version, task);
            }
        }
        return assignedTask;
    }

    public OperationStatusRest changeExecContextState(String state, Long execContextId, DispatcherContext context) {
        return execContextSyncService.getWithSync(execContextId,
                ()-> execContextFSM.changeExecContextState(state, execContextId, context.getCompanyId()));
    }

    public OperationStatusRest execContextTargetState(Long execContextId, EnumsApi.ExecContextState execState, Long companyUniqueId) {
        return execContextSyncService.getWithSync(execContextId,
                ()-> execContextFSM.changeExecContextStateWithTx(execState, execContextId, companyUniqueId));
    }

    public void updateExecContextStatus(Long execContextId, boolean needReconciliation) {
        execContextSyncService.getWithSyncNullable(execContextId, () -> execContextFSM.updateExecContextStatus(execContextId, needReconciliation));
    }

    public OperationStatusRest resetTask(Long taskId) {
        Long execContextId = taskRepository.getExecContextId(taskId);
        if (execContextId==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#705.080 Can't re-run task "+taskId+", task with such taskId wasn't found");
        }

        return execContextSyncService.getWithSync(execContextId, () -> execContextFSM.resetTaskWithTx(execContextId, taskId));
    }

    public EnumsApi.TaskProducingStatus toProducing(Long execContextId) {
        return execContextSyncService.getWithSync(execContextId, () -> execContextFSM.toProducing(execContextId));
    }

    private void storeExecResult(ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result) {
        Long execContextId = taskRepository.getExecContextId(result.taskId);
        if (execContextId==null) {
            log.warn("Reporting about non-existed task #{}", result.taskId);
            return;
        }
        execContextSyncService.getWithSyncNullable(execContextId, () -> execContextFSM.storeExecResultWithTx(result));
    }

    @Async
    @EventListener
    public void reconcileStates(final ReconcileStatesEvent event) {
        reconcileStates(event.execContextId);
    }

    public void reconcileStates(Long execContextId) {
        execContextSyncService.getWithSyncNullable(execContextId, () -> execContextFSM.reconcileStatesWithTx(execContextId));
    }

    public void processResendTaskOutputResourceResult(@Nullable String processorId, Enums.ResendTaskOutputResourceStatus status, Long taskId, Long variableId) {
        Long execContextId = taskRepository.getExecContextId(taskId);
        if (execContextId==null) {
            log.warn("#317.020 Task obsolete and was already deleted");
            return;
        }

        execContextSyncService.getWithSyncNullable(execContextId,
                () -> execContextFSM.processResendTaskOutputVariable(processorId, status, taskId, variableId));
    }

    // TODO 2019.05.19 add reporting of producing of tasks
    // TODO 2020.01.17 reporting to where? do we need to implement it?
    // TODO 2020.09.28 reporting is about dynamically inform a web application about the current status of creating
    public synchronized void createAllTasks() {

        List<ExecContextImpl> execContexts = execContextRepository.findByState(EnumsApi.ExecContextState.PRODUCING.code);
        if (!execContexts.isEmpty()) {
            log.info("#701.020 Start producing tasks");
        }
        for (ExecContextImpl execContext : execContexts) {
            execContextSyncService.getWithSyncNullable(execContext.id, ()-> {
                log.info("#701.030 Producing tasks for sourceCode.code: {}, input resource pool: \n{}", execContext.getSourceCodeId(), execContext.getParams());
                execContextTaskProducingService.produceAllTasks(true, execContext);
                return null;
            });
        }
        if (!execContexts.isEmpty()) {
            log.info("#701.040 Producing of tasks was finished");
        }
    }



}

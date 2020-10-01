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
import ai.metaheuristic.ai.Monitoring;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.event.ReconcileStatesEvent;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.dispatcher.ExecContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 7/4/2019
 * Time: 3:56 PM
 */
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class ExecContextTopLevelService {

    private final ExecContextCache execContextCache;
    private final ExecContextService execContextService;
    private final SourceCodeCache sourceCodeCache;
    private final ExecContextRepository execContextRepository;
    private final ExecContextSyncService execContextSyncService;
    private final ExecContextFSM execContextFSM;
    private final TaskRepository taskRepository;
    private final TaskProducingService taskProducingService;

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
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext == null) {
            return new SourceCodeApiData.ExecContextResult("#705.180 execContext wasn't found, execContextId: " + execContextId);
        }
        SourceCodeImpl sourceCode = sourceCodeCache.findById(execContext.getSourceCodeId());
        if (sourceCode == null) {
            return new SourceCodeApiData.ExecContextResult("#705.200 sourceCode wasn't found, sourceCodeId: " + execContext.getSourceCodeId());
        }

        if (!sourceCode.getId().equals(execContext.getSourceCodeId())) {
            execContextService.changeValidStatus(execContextId, false);
            return new SourceCodeApiData.ExecContextResult("#705.220 sourceCodeId doesn't match to execContext.sourceCodeId, sourceCodeId: " + execContext.getSourceCodeId() + ", execContext.sourceCodeId: " + execContext.getSourceCodeId());
        }

        SourceCodeApiData.ExecContextResult result = new SourceCodeApiData.ExecContextResult(sourceCode, execContext);
        return result;
    }

    @Nullable
    public DispatcherCommParamsYaml.AssignedTask findTaskInExecContext(ProcessorCommParamsYaml.ReportProcessorTaskStatus reportProcessorTaskStatus, Long processorId, boolean isAcceptOnlySigned, Long execContextId) {
        DispatcherCommParamsYaml.AssignedTask assignedTask = execContextSyncService.getWithSync(execContextId, ()-> {
            ExecContextImpl execContext = execContextCache.findById(execContextId);
            if (execContext == null) {
                log.error("#705.315Cache doesn't contain ExecContext #{}", execContextId);
                return null;
            }
            if (execContext.state != EnumsApi.ExecContextState.STARTED.code) {
                return null;
            }
            DispatcherCommParamsYaml.AssignedTask task = getTaskAndAssignToProcessor(
                    reportProcessorTaskStatus, processorId, isAcceptOnlySigned, execContextId);
            return task;
        });
        return assignedTask;
    }

    @Nullable
    public DispatcherCommParamsYaml.AssignedTask getTaskAndAssignToProcessor(ProcessorCommParamsYaml.ReportProcessorTaskStatus reportProcessorTaskStatus, Long processorId, boolean isAcceptOnlySigned, Long execContextId) {
        return execContextSyncService.getWithSync(execContextId,
                ()-> execContextFSM.getTaskAndAssignToProcessor(reportProcessorTaskStatus, processorId, isAcceptOnlySigned, execContextId));
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

    public OperationStatusRest changeExecContextState(String state, Long execContextId, DispatcherContext context) {
        return execContextSyncService.getWithSync(execContextId,
                ()-> execContextFSM.changeExecContextState(state, execContextId, context.getCompanyId()));
    }

    public OperationStatusRest execContextTargetState(Long execContextId, EnumsApi.ExecContextState execState, Long companyUniqueId) {
        return execContextSyncService.getWithSync(execContextId,
                ()-> execContextFSM.changeExecContextState(execState, execContextId, companyUniqueId));
    }

    public void updateExecContextStatus(Long execContextId, boolean needReconciliation) {
        execContextSyncService.getWithSyncNullable(execContextId, () -> execContextFSM.updateExecContextStatus(execContextId, needReconciliation));
    }

    @Nullable
    private OperationStatusRest checkExecContext(Long execContextId, DispatcherContext context) {
        ExecContext wb = execContextCache.findById(execContextId);
        if (wb==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.400 ExecContext wasn't found, execContextId: " + execContextId );
        }
        return null;
    }

    public OperationStatusRest resetTask(Long taskId) {
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#705.080 Can't re-run task "+taskId+", task with such taskId wasn't found");
        }

        return execContextSyncService.getWithSync(task.execContextId, () -> execContextFSM.resetTask(task.execContextId, taskId));
    }

    public EnumsApi.TaskProducingStatus toProducing(Long execContextId) {
        return execContextSyncService.getWithSync(execContextId, () -> execContextFSM.toProducing(execContextId));
    }

    public void storeExecResult(ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result) {
        TaskImpl task = taskRepository.findById(result.taskId).orElse(null);
        if (task==null) {
            log.warn("Reporting about non-existed task #{}", result.taskId);
            return;
        }
        execContextSyncService.getWithSyncNullable(task.execContextId, () -> {
            execContextFSM.storeExecResult(result);
            return null;
        });
    }

    @Async
    @EventListener
    public void reconcileStates(final ReconcileStatesEvent event) {
        reconcileStates(event.execContextId);
    }

    public void reconcileStates(Long execContextId) {
        execContextSyncService.getWithSyncNullable(execContextId, () -> execContextFSM.reconcileStates(execContextId));
    }

        public void processResendTaskOutputResourceResult(@Nullable String processorId, Enums.ResendTaskOutputResourceStatus status, Long taskId, Long variableId) {
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("#317.020 Task obsolete and was already deleted");
            return;
        }

        execContextSyncService.getWithSyncNullable(task.execContextId, () -> {
            execContextFSM.processResendTaskOutputResourceResult(processorId, status, task, variableId);
            return null;
        });
    }

    // TODO 2019.05.19 add reporting of producing of tasks
    // TODO 2020.01.17 reporting to where? do we need to implement it?
    // TODO 2020.09.28 reporting is about dynamically inform a web application about the current status of creating
    public synchronized void createAllTasks() {

        Monitoring.log("##019", Enums.Monitor.MEMORY);
        List<ExecContextImpl> execContexts = execContextRepository.findByState(EnumsApi.ExecContextState.PRODUCING.code);
        Monitoring.log("##020", Enums.Monitor.MEMORY);
        if (!execContexts.isEmpty()) {
            log.info("#701.020 Start producing tasks");
        }
        for (ExecContextImpl execContext : execContexts) {
            execContextSyncService.getWithSyncNullable(execContext.id, ()-> {
                SourceCodeImpl sourceCode = sourceCodeCache.findById(execContext.getSourceCodeId());
                if (sourceCode == null) {
                    execContextFSM.toStopped(execContext.id);
                    return null;
                }
                Monitoring.log("##021", Enums.Monitor.MEMORY);
                log.info("#701.030 Producing tasks for sourceCode.code: {}, input resource pool: \n{}", sourceCode.uid, execContext.getParams());
                taskProducingService.produceAllTasks(true, sourceCode, execContext);
                Monitoring.log("##022", Enums.Monitor.MEMORY);
                return null;
            });
        }
        if (!execContexts.isEmpty()) {
            log.info("#701.040 Producing of tasks was finished");
        }
    }



}

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

import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.event.DeregisterTasksByExecContextIdEvent;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.event.RegisterTaskForProcessingEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextStatusService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTaskStateService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Serge
 * Date: 10/11/2020
 * Time: 5:38 AM
 */
@SuppressWarnings("DuplicatedCode")
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class TaskProviderService {

    private final TaskProviderTransactionalService taskProviderTransactionalService;
    private final DispatcherEventService dispatcherEventService;
    private final ExecContextSyncService execContextSyncService;
    private final ExecContextTaskStateService execContextTaskStateService;
    private final TaskRepository taskRepository;
    private final ProcessorCache processorCache;
    private final ExecContextStatusService execContextStatusService;

    private static final Object syncObj = new Object();

    @Async
    @EventListener
    public void registerTask(RegisterTaskForProcessingEvent event) {
        synchronized (syncObj) {
            taskProviderTransactionalService.registerTask(event);
        }
    }

    @Async
    @EventListener
    public void deregisterTasksByExecContextId(DeregisterTasksByExecContextIdEvent event) {
        synchronized (syncObj) {
            taskProviderTransactionalService.deregisterTasksByExecContextId(event.execContextId);
        }
    }

    public boolean isQueueEmpty() {
        return taskProviderTransactionalService.isQueueEmpty();
    }

    @Nullable
    private TaskImpl findUnassignedTaskAndAssign(Processor processor, ProcessorStatusYaml psy, boolean isAcceptOnlySigned) {
        TxUtils.checkTxNotExists();
        synchronized (syncObj) {

            if (taskProviderTransactionalService.isQueueEmpty()) {
                return null;
            }

            TaskImpl task = taskProviderTransactionalService.findUnassignedTaskAndAssign(processor, psy, isAcceptOnlySigned);
            if (task!=null) {
                execContextSyncService.getWithSyncNullable(task.execContextId,
                        ()->execContextTaskStateService.updateTaskExecStatesWithTx(task.execContextId, task.id, EnumsApi.TaskExecState.IN_PROGRESS, null));

                dispatcherEventService.publishTaskEvent(EnumsApi.DispatcherEventType.TASK_ASSIGNED, processor.id, task.id, task.execContextId);
                taskProviderTransactionalService.deRegisterTask(task.id);
            }
            return task;
        }
    }

    @Nullable
    public DispatcherCommParamsYaml.AssignedTask findTask(ProcessorCommParamsYaml.ReportProcessorTaskStatus reportProcessorTaskStatus, Long processorId, boolean isAcceptOnlySigned) {
        TxUtils.checkTxNotExists();
        if (taskProviderTransactionalService.isQueueEmpty()) {
            return null;
        }

        final Processor processor = processorCache.findById(processorId);
        if (processor == null) {
            log.error("#393.020 Processor with id #{} wasn't found", processorId);
            return null;
        }
        ProcessorStatusYaml psy = toProcessorStatusYaml(processor);
        if (psy==null) {
            return null;
        }

        DispatcherCommParamsYaml.AssignedTask assignedTask = getTaskAndAssignToProcessor(
                reportProcessorTaskStatus, processor, psy, isAcceptOnlySigned);

        if (assignedTask!=null && log.isDebugEnabled()) {
            TaskImpl task = taskRepository.findById(assignedTask.taskId).orElse(null);
            if (task==null) {
                log.debug("#393.040 findTask(), task #{} wasn't found", assignedTask.taskId);
            }
            else {
                log.debug("#393.060 findTask(), task id: #{}, ver: {}, task: {}", task.id, task.version, task);
            }
        }
        return assignedTask;
    }

    @Nullable
    private static ProcessorStatusYaml toProcessorStatusYaml(Processor processor) {
        ProcessorStatusYaml ss;
        try {
            ss = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(processor.status);
            return ss;
        } catch (Throwable e) {
            log.error("#393.080 Error parsing current status of processor:\n{}", processor.status);
            log.error("#393.100 Error ", e);
            return null;
        }
    }

    @Nullable
    private DispatcherCommParamsYaml.AssignedTask getTaskAndAssignToProcessor(
            ProcessorCommParamsYaml.ReportProcessorTaskStatus reportProcessorTaskStatus, Processor processor, ProcessorStatusYaml psy, boolean isAcceptOnlySigned) {
        TxUtils.checkTxNotExists();

        final TaskImpl task = getTaskAndAssignToProcessorInternal(reportProcessorTaskStatus, processor, psy, isAcceptOnlySigned);
        // task won't be returned for an internal function
        if (task==null) {
            return null;
        }
        try {
            String params;
            try {
                TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
                if (tpy.version == psy.taskParamsVersion) {
                    params = task.params;
                } else {
                    params = TaskParamsYamlUtils.BASE_YAML_UTILS.toStringAsVersion(tpy, psy.taskParamsVersion);
                }
            } catch (DowngradeNotSupportedException e) {
                // TODO 2020-09-267 there is a possible situation when a check in ExecContextFSM.findUnassignedTaskAndAssign() would be ok
                //  but this one fails. that could occur because of prepareVariables(task);
                //  need a better solution for checking
                log.warn("#393.120 Task #{} can't be assigned to processor #{} because it's too old, downgrade to required taskParams level {} isn't supported",
                        task.getId(), processor.id, psy.taskParamsVersion);
                return null;
            }

            return new DispatcherCommParamsYaml.AssignedTask(params, task.getId(), task.getExecContextId());
        } catch (Throwable th) {
            String es = "#393.140 Something wrong";
            log.error(es, th);
            throw new IllegalStateException(es, th);
        }
    }

    @Nullable
    private TaskImpl getTaskAndAssignToProcessorInternal(
            ProcessorCommParamsYaml.ReportProcessorTaskStatus reportProcessorTaskStatus, Processor processor, ProcessorStatusYaml psy, boolean isAcceptOnlySigned) {
        TxUtils.checkTxNotExists();

        DispatcherCommParamsYaml.ExecContextStatus statuses = execContextStatusService.getExecContextStatuses();

        List<TaskImpl> tasks = taskRepository.findForProcessorId(processor.id);
        for (TaskImpl task : tasks) {
            if (!statuses.isStarted(task.execContextId)) {
                continue;
            }
            if (reportProcessorTaskStatus.statuses==null || reportProcessorTaskStatus.statuses.stream().noneMatch(a->a.taskId==task.id)) {
                if (task.execState==EnumsApi.TaskExecState.IN_PROGRESS.value) {
                    log.warn("#393.160 already assigned task, processor: #{}, task #{}, execStatus: {}",
                            processor.id, task.id, EnumsApi.TaskExecState.from(task.execState));
                    return task;
                }
            }
        }

        TaskImpl result = findUnassignedTaskAndAssign(processor, psy, isAcceptOnlySigned);
        return result;
    }


}

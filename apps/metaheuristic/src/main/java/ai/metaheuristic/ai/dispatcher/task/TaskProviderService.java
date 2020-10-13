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
import java.util.stream.Collectors;

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

    private static final Object syncObj = new Object();

    @Async
    @EventListener
    public void registerTask(RegisterTaskForProcessingEvent event) {
        synchronized (syncObj) {
            taskProviderTransactionalService.registerTask(event.execContextId, event.taskId);
        }
    }

    public int countOfTasks() {
        synchronized (syncObj) {
            return taskProviderTransactionalService.countOfTasks();
        }
    }

    @Async
    @EventListener
    public void deregisterTasksByExecContextId(DeregisterTasksByExecContextIdEvent event) {
        synchronized (syncObj) {
            taskProviderTransactionalService.deregisterTasksByExecContextId(event.execContextId);
        }
    }

    @Nullable
    private TaskImpl findUnassignedTaskAndAssign(Long processorId, ProcessorStatusYaml psy, boolean isAcceptOnlySigned) {
        TxUtils.checkTxNotExists();
        synchronized (syncObj) {
            TaskImpl task = taskProviderTransactionalService.findUnassignedTaskAndAssign(processorId, psy, isAcceptOnlySigned);
            if (task!=null) {
                execContextSyncService.getWithSyncNullable(task.execContextId,
                        ()->execContextTaskStateService.updateTaskExecStatesWithTx(task.execContextId, task.id, EnumsApi.TaskExecState.IN_PROGRESS, null));

                dispatcherEventService.publishTaskEvent(EnumsApi.DispatcherEventType.TASK_ASSIGNED, processorId, task.id, task.execContextId);
                taskProviderTransactionalService.deRegisterTask(task.id);
            }
            return task;
        }
    }

    @Nullable
    public DispatcherCommParamsYaml.AssignedTask findTaskInExecContext(ProcessorCommParamsYaml.ReportProcessorTaskStatus reportProcessorTaskStatus, Long processorId, boolean isAcceptOnlySigned) {
        final Processor processor = processorCache.findById(processorId);
        if (processor == null) {
            log.error("#303.620 Processor with id #{} wasn't found", processorId);
            return null;
        }
        ProcessorStatusYaml psy = toProcessorStatusYaml(processor);
        if (psy==null) {
            return null;
        }

        DispatcherCommParamsYaml.AssignedTask assignedTask = getTaskAndAssignToProcessor(
                reportProcessorTaskStatus, processorId, psy, isAcceptOnlySigned);

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

    @Nullable
    private ProcessorStatusYaml toProcessorStatusYaml(Processor processor) {
        ProcessorStatusYaml ss;
        try {
            ss = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(processor.status);
            return ss;
        } catch (Throwable e) {
            log.error("#303.800 Error parsing current status of processor:\n{}", processor.status);
            log.error("#303.820 Error ", e);
            return null;
        }
    }

    @Nullable
    private DispatcherCommParamsYaml.AssignedTask getTaskAndAssignToProcessor(
            ProcessorCommParamsYaml.ReportProcessorTaskStatus reportProcessorTaskStatus, Long processorId, ProcessorStatusYaml psy, boolean isAcceptOnlySigned) {
        TxUtils.checkTxNotExists();

        final TaskImpl task = getTaskAndAssignToProcessorInternal(reportProcessorTaskStatus, processorId, psy, isAcceptOnlySigned);
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
                log.warn("#303.660 Task #{} can't be assigned to processor #{} because it's too old, downgrade to required taskParams level {} isn't supported",
                        task.getId(), processorId, psy.taskParamsVersion);
                return null;
            }

            return new DispatcherCommParamsYaml.AssignedTask(params, task.getId(), task.getExecContextId());
        } catch (Throwable th) {
            String es = "#303.680 Something wrong";
            log.error(es, th);
            throw new IllegalStateException(es, th);
        }
    }

    @Nullable
    private TaskImpl getTaskAndAssignToProcessorInternal(
            ProcessorCommParamsYaml.ReportProcessorTaskStatus reportProcessorTaskStatus, Long processorId, ProcessorStatusYaml psy, boolean isAcceptOnlySigned) {
        TxUtils.checkTxNotExists();

        List<Long> activeTaskIds = taskRepository.findActiveForProcessorId(processorId);
        boolean allAssigned = false;
        if (reportProcessorTaskStatus.statuses!=null) {
            List<ProcessorCommParamsYaml.ReportProcessorTaskStatus.SimpleStatus> lostTasks = reportProcessorTaskStatus.statuses.stream()
                    .filter(o->!activeTaskIds.contains(o.taskId)).collect(Collectors.toList());
            if (lostTasks.isEmpty()) {
                allAssigned = true;
            }
            else {
                log.info("#303.740 Found the lost tasks at processor #{}, tasks #{}", processorId, lostTasks);
                for (ProcessorCommParamsYaml.ReportProcessorTaskStatus.SimpleStatus lostTask : lostTasks) {
                    TaskImpl task = taskRepository.findById(lostTask.taskId).orElse(null);
                    if (task==null) {
                        continue;
                    }
                    if (task.execState!= EnumsApi.TaskExecState.IN_PROGRESS.value) {
                        log.warn("#303.760 !!! Need to investigate, processor: #{}, task #{}, execStatus: {}, but isCompleted==false",
                                processorId, task.id, EnumsApi.TaskExecState.from(task.execState));
                        // TODO 2020-09-26 because this situation shouldn't be happened what exactly to do isn't known right now
                    }
                    return task;
                }
            }
        }
        if (allAssigned) {
            log.warn("#303.780 ! This processor already has active task. Need to investigate, shouldn't happened, processor #{}, tasks: {}", processorId, activeTaskIds);
            return null;
        }

        TaskImpl result = findUnassignedTaskAndAssign(processorId, psy, isAcceptOnlySigned);

        return result;
    }


}

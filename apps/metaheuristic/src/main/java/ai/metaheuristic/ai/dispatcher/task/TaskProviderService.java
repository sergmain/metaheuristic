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
import ai.metaheuristic.ai.dispatcher.event.*;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextStatusService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
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
    private final TaskRepository taskRepository;
    private final ProcessorCache processorCache;
    private final ExecContextStatusService execContextStatusService;
    private final ApplicationEventPublisher applicationEventPublisher;

    private static class TaskProviderServiceSync {}

    // this sync is here because of the presence of @Transactional in TaskProviderTransactionalService
    // so we don't want to have an opened TX with holding sync within it
    public static final TaskProviderServiceSync syncObj = new TaskProviderServiceSync();

    public void registerTask(Long execContextId, Long taskId) {
        synchronized (syncObj) {
            taskProviderTransactionalService.registerTask(execContextId, taskId);
        }
    }

    @Async
    @EventListener
    public void processDeletedExecContext(ProcessDeletedExecContextEvent event) {
        synchronized (syncObj) {
            taskProviderTransactionalService.processDeletedExecContext(event);
        }
    }

    @Async
    @EventListener
    public void deregisterTasksByExecContextId(DeregisterTasksByExecContextIdEvent event) {
        synchronized (syncObj) {
            taskProviderTransactionalService.deregisterTasksByExecContextId(event.execContextId);
        }
    }

    public void deregisterTask(Long execContextId, Long taskId) {
        synchronized (syncObj) {
            taskProviderTransactionalService.deRegisterTask(execContextId, taskId);
        }
    }

    public boolean isQueueEmpty() {
        synchronized (syncObj) {
            return taskProviderTransactionalService.isQueueEmpty();
        }
    }

    public void registerInternalTask(Long execContextId, Long taskId) {
        synchronized (syncObj) {
            taskProviderTransactionalService.registerInternalTask(execContextId, taskId);
        }
    }

    @Async
    @EventListener
    public void setTaskExecState(SetTaskExecStateEvent event) {
        setTaskExecState(event.execContextId, event.taskId, event.state);
    }

    public void setTaskExecState(Long execContextId, Long taskId, EnumsApi.TaskExecState state) {
        synchronized (syncObj) {
            if (taskProviderTransactionalService.setTaskExecState(execContextId, taskId, state)) {
                applicationEventPublisher.publishEvent(new TransferStateFromTaskQueueToExecContextEvent(execContextId));
            }
        }
    }

    @Nullable
    private TaskImpl findUnassignedTaskAndAssign(Processor processor, ProcessorStatusYaml psy, boolean isAcceptOnlySigned) {
        TxUtils.checkTxNotExists();

        TaskImpl task;
        synchronized (syncObj) {
            if (taskProviderTransactionalService.isQueueEmpty()) {
                return null;
            }
            task = taskProviderTransactionalService.findUnassignedTaskAndAssign(processor, psy, isAcceptOnlySigned);
        }
        if (task!=null) {
            dispatcherEventService.publishTaskEvent(EnumsApi.DispatcherEventType.TASK_ASSIGNED, processor.id, task.id, task.execContextId);
        }
        return task;
    }

    private static final Map<Long, AtomicLong> processorCheckedOn = new HashMap<>();

    @Nullable
    public DispatcherCommParamsYaml.AssignedTask findTask(Long processorId, boolean isAcceptOnlySigned) {
        TxUtils.checkTxNotExists();

        final Processor processor = processorCache.findById(processorId);
        if (processor == null) {
            log.error("#393.020 Processor with id #{} wasn't found", processorId);
            return null;
        }

        if (taskProviderTransactionalService.isQueueEmpty()) {
            AtomicLong mills = processorCheckedOn.computeIfAbsent(processor.id, o -> new AtomicLong());
            if (System.currentTimeMillis()-mills.get() < 60_000 ) {
                return null;
            }
            mills.set(System.currentTimeMillis());
        }

        ProcessorStatusYaml psy = toProcessorStatusYaml(processor);
        if (psy==null) {
            return null;
        }

        DispatcherCommParamsYaml.AssignedTask assignedTask = getTaskAndAssignToProcessor(processor, psy, isAcceptOnlySigned);

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
    private DispatcherCommParamsYaml.AssignedTask getTaskAndAssignToProcessor(Processor processor, ProcessorStatusYaml psy, boolean isAcceptOnlySigned) {
        TxUtils.checkTxNotExists();

        final TaskImpl task = getTaskAndAssignToProcessorInternal(processor, psy, isAcceptOnlySigned);
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
                // TODO 2020-09-26 there is a possible situation when a check in ExecContextFSM.findUnassignedTaskAndAssign() would be ok
                //  but this one fails. that could occur because of prepareVariables(task);
                //  need a better solution for checking
                log.warn("#393.120 Task #{} can't be assigned to processor #{} because it's too old, downgrade to required taskParams level {} isn't supported",
                        task.getId(), processor.id, psy.taskParamsVersion);
                return null;
            }

            // because we're already providing ith task that means that execCOntext was started
            return new DispatcherCommParamsYaml.AssignedTask(params, task.getId(), task.getExecContextId(), EnumsApi.ExecContextState.STARTED);

        } catch (Throwable th) {
            String es = "#393.140 Something wrong";
            log.error(es, th);
            throw new IllegalStateException(es, th);
        }
    }

    @Nullable
    private TaskImpl getTaskAndAssignToProcessorInternal(Processor processor, ProcessorStatusYaml psy, boolean isAcceptOnlySigned) {
        TxUtils.checkTxNotExists();

        KeepAliveResponseParamYaml.ExecContextStatus statuses = execContextStatusService.getExecContextStatuses();

        List<Long> taskIds = S.b(psy.taskIds) ?
                List.of() :
                Arrays.stream(StringUtils.split(psy.taskIds, ", ")).map(Long::parseLong).collect(Collectors.toList());

        List<TaskImpl> tasks = taskRepository.findForProcessorId(processor.id);
        for (TaskImpl task : tasks) {
            if (!statuses.isStarted(task.execContextId)) {
                continue;
            }
            if (!taskIds.contains(task.id)) {
                if (task.execState==EnumsApi.TaskExecState.IN_PROGRESS.value) {
                    log.warn("#393.160 already assigned task, processor: #{}, task #{}, execStatus: {}",
                            processor.id, task.id, EnumsApi.TaskExecState.from(task.execState));
//                    deregisterTask(task.execContextId, task.id);
                    return task;
                }
            }
        }

        TaskImpl result = findUnassignedTaskAndAssign(processor, psy, isAcceptOnlySigned);
        return result;
    }


}

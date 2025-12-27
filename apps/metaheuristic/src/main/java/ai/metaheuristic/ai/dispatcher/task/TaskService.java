/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.event.events.*;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Serge
 * Date: 9/29/2020
 * Time: 7:22 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class TaskService {

    private final ExecContextCache execContextCache;
    private final TaskTxService taskTxService;
    private final TaskRepository taskRepository;
    private final VariableTxService variableService;
    private final TaskVariableTopLevelService taskVariableTopLevelService;
    private final ApplicationEventPublisher applicationEventPublisher;

    private final Map<Long, AtomicLong> lastCheckOn = new HashMap<>();
    private static final int MILLS_TO_HOLD_CHECK = 30_000;

    private final LinkedList<CheckForLostTaskEvent> queue = new LinkedList<>();
    public final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);

    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    @Async
    @EventListener
    public void handleCheckForLostTaskEvent(final CheckForLostTaskEvent event) {
        putToQueue(event);
        processCheckForLostTaskEvent();
    }

    private void putToQueue(final CheckForLostTaskEvent event) {
        writeLock.lock();
        try {
            final long completedTaskCount = executor.getCompletedTaskCount();
            final long taskCount = executor.getTaskCount();
            if ((taskCount - completedTaskCount)>20) {
                return;
            }

            AtomicLong lastCheck = lastCheckOn.computeIfAbsent(event.coreId, (o)->new AtomicLong());
            if (System.currentTimeMillis() - lastCheck.get() > MILLS_TO_HOLD_CHECK) {
                boolean found = false;
                for (CheckForLostTaskEvent checkForLostTaskEvent : queue) {
                    if (event.coreId.equals(checkForLostTaskEvent.coreId)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    queue.add(event);
                    lastCheck.set(System.currentTimeMillis());
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Nullable
    private CheckForLostTaskEvent pullFromQueue() {
        writeLock.lock();
        try {
            return queue.pollFirst();
        } finally {
            writeLock.unlock();
        }
    }

    public void processCheckForLostTaskEvent() {
        // Don't change to virtual thread
        Thread t = new Thread(() -> {
            CheckForLostTaskEvent currEvent;
            while ((currEvent = pullFromQueue()) != null) {
                checkForLostTaskInternal(currEvent);
            }
        }, "TaskService-" + ThreadUtils.nextThreadNum());
        executor.submit(t);
    }

    private void checkForLostTaskInternal(final CheckForLostTaskEvent event) {
        if (event.taskIds.isEmpty()) {
            return;
        }
        List<Long> actualTaskIds = taskRepository.findTaskIdsForCoreId(event.coreId);
        for (Long actualTaskId : actualTaskIds) {
            if (!event.taskIds.contains(actualTaskId)) {
                TaskImpl task = taskRepository.findById(actualTaskId).orElse(null);
                if (task!=null) {
                    ExecContextImpl ec = execContextCache.findById(task.execContextId, true);
                    if (ec==null || EnumsApi.ExecContextState.isFinishedState(ec.state)) {
                        continue;
                    }
                    if (EnumsApi.TaskExecState.isFinishedState(task.execState)) {
                        continue;
                    }
                }
                if (task==null || EnumsApi.TaskExecState.IN_PROGRESS!=EnumsApi.TaskExecState.from(task.execState)) {
                    continue;
                }
                if (task.assignedOn==null || (System.currentTimeMillis() - task.assignedOn<60_000)) {
                    continue;
                }
                // #303.370 found a lost task #310927, which doesn't exist at processor #352. task exists in db: true, state: OK
                log.info("#303.370 found a lost task #{}, which doesn't exist at core #{}. state: {}, assignedOn: {}, is old: {}",
                        actualTaskId, event.coreId, EnumsApi.TaskExecState.from(task.execState),
                        task.assignedOn, System.currentTimeMillis() - task.assignedOn<60_000);

                applicationEventPublisher.publishEvent(new ResetTaskEvent(task.execContextId, actualTaskId));
            }
        }
    }

    @Async
    @EventListener
    public void handleTaskCommunicationEvent(TaskCommunicationEvent event) {
        taskTxService.updateAccessByProcessorOn(event.taskId);
    }

    public void processResendTaskOutputResourceResult(@Nullable Long processorId, Enums.ResendTaskOutputResourceStatus status, Long taskId, Long variableId) {
        switch (status) {
            case SEND_SCHEDULED:
                log.info("#303.380 Processor #{} scheduled for sending an output variable #{}, task #{}. This is normal operation of Processor", processorId, variableId, taskId);
                break;
            case TASK_NOT_FOUND:
            case VARIABLE_NOT_FOUND:
            case TASK_IS_BROKEN:
            case TASK_PARAM_FILE_NOT_FOUND:
                applicationEventPublisher.publishEvent(new TaskFinishWithErrorEvent(taskId,
                        "#303.390 Task #"+taskId+" was finished while resending variable #"+variableId+" with status " + status));

                break;
            case OUTPUT_RESOURCE_ON_EXTERNAL_STORAGE:
                applicationEventPublisher.publishEvent(new SetVariableReceivedTxEvent(taskId, variableId, false).to());
                break;
        }
    }

    public DispatcherCommParamsYaml.ResendTaskOutputs variableReceivingChecker(Long processorId) {
        List<TaskImpl> tasks = taskRepository.findForMissingResultVariables(processorId, System.currentTimeMillis(), EnumsApi.TaskExecState.OK.value);
        DispatcherCommParamsYaml.ResendTaskOutputs result = new DispatcherCommParamsYaml.ResendTaskOutputs();
        for (TaskImpl task : tasks) {
            ExecContextImpl ec = execContextCache.findById(task.execContextId, true);
            if (ec==null || EnumsApi.ExecContextState.isFinishedState(ec.state)) {
                continue;
            }
            TaskParamsYaml tpy = task.getTaskParamsYaml();
            for (TaskParamsYaml.OutputVariable output : tpy.task.outputs) {
                if (!output.uploaded) {
                    Variable sv = variableService.getVariable(output.id);
                    if (sv!=null && sv.inited) {
                        TaskSyncService.getWithSync(task.id, () -> taskVariableTopLevelService.updateStatusOfVariable(task.id, output.id));
                    }
                    else {
                        result.resends.add(new DispatcherCommParamsYaml.ResendTaskOutput(task.getId(), output.id));
                    }
                }
            }
        }
        return result;
    }

}

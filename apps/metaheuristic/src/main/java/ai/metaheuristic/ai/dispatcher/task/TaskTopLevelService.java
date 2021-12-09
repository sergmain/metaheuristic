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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.event.*;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Serge
 * Date: 9/29/2020
 * Time: 7:22 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskTopLevelService {

    private final TaskService taskService;
    private final TaskRepository taskRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Async
    @EventListener
    public void handleTaskCommunicationEvent(TaskCommunicationEvent event) {
        taskService.updateAccessByProcessorOn(event.taskId);
    }

    public final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
    private final LinkedList<CheckForLostTaskEvent> queue = new LinkedList<>();
    private final Map<Long, AtomicLong> lastCheckOn = new HashMap<>();

    private static final int MILLS_TO_HOLD_CHECK = 30_000;

    private void putToQueue(final CheckForLostTaskEvent event) {
        synchronized (queue) {
            final long completedTaskCount = executor.getCompletedTaskCount();
            final long taskCount = executor.getTaskCount();
            if ((taskCount - completedTaskCount)>20) {
                return;
            }

            AtomicLong lastCheck = lastCheckOn.computeIfAbsent(event.processorId, (o)->new AtomicLong());
            if (System.currentTimeMillis() - lastCheck.get() > MILLS_TO_HOLD_CHECK) {
                boolean found = false;
                for (CheckForLostTaskEvent checkForLostTaskEvent : queue) {
                    if (event.processorId.equals(checkForLostTaskEvent.processorId)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    queue.add(event);
                    lastCheck.set(System.currentTimeMillis());
                }
            }
        }
    }

    @Nullable
    private CheckForLostTaskEvent pullFromQueue() {
        synchronized (queue) {
            return queue.pollFirst();
        }
    }

    @Async
    @EventListener
    public void handleCheckForLostTaskEvent(final CheckForLostTaskEvent event) {
        putToQueue(event);

        executor.submit(() -> {
            CheckForLostTaskEvent currEvent;
            while ((currEvent = pullFromQueue()) != null) {
                checkForLostTaskInternal(currEvent);
            }
        });
    }

    private void checkForLostTaskInternal(final CheckForLostTaskEvent event) {
        if (event.taskIds.isEmpty()) {
            return;
        }
        List<Long> actualTaskIds = taskRepository.findTaskIdsForProcessorId(event.processorId);
        for (Long actualTaskId : actualTaskIds) {
            if (!event.taskIds.contains(actualTaskId)) {
                TaskImpl task = taskRepository.findById(actualTaskId).orElse(null);
                log.warn("#303.370 found a lost task #{}, which doesn't exist at processor #{}. task exists in db: {}, state: {}",
                        actualTaskId, event.processorId, (task!=null), (task!=null) ? EnumsApi.TaskExecState.from(task.execState) : null);
                if (task==null || EnumsApi.TaskExecState.IN_PROGRESS!=EnumsApi.TaskExecState.from(task.execState)) {
                    return;
                }
                log.warn("#303.375 found a lost task #{}, assignedOn: {}, is old: {}",
                        actualTaskId, task.assignedOn, task.assignedOn!=null ? (System.currentTimeMillis() - task.assignedOn<60_000) : null);

                if (task.assignedOn==null || (System.currentTimeMillis() - task.assignedOn<60_000)) {
                    return;
                }
                applicationEventPublisher.publishEvent(new ResetTaskEvent(task.execContextId, actualTaskId));
            }
        }
    }

    public void processResendTaskOutputResourceResult(@Nullable String processorId, Enums.ResendTaskOutputResourceStatus status, Long taskId, Long variableId) {
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


}

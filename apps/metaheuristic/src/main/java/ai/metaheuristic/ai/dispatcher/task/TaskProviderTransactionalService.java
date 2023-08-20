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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.data.DispatcherData;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.QuotasData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.events.PostTaskAssigningRollbackTxEvent;
import ai.metaheuristic.ai.dispatcher.event.events.PostTaskAssigningTxEvent;
import ai.metaheuristic.ai.dispatcher.event.events.StartTaskProcessingTxEvent;
import ai.metaheuristic.ai.dispatcher.event.events.UnAssignTaskTxEvent;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static ai.metaheuristic.ai.dispatcher.task.TaskQueueSyncStaticService.checkWriteLockNotPresent;

/**
 * @author Serge
 * Date: 10/11/2020
 * Time: 3:25 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class TaskProviderTransactionalService {

    private final TaskRepository taskRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EventPublisherService eventPublisherService;

    @Nullable
    @Transactional
    public TaskData.AssignedTask findUnassignedTaskAndAssign(
            Long coreId, final DispatcherData.TaskQuotas currentQuotas,
            final TaskQueue.AllocatedTask resultTask, final QuotasData.ActualQuota quota
    ) {
        checkWriteLockNotPresent();
        if (resultTask.queuedTask.task == null) {
            throw new IllegalStateException("#317.100 (resultTask.queuedTask.task == null)");
        }

        TaskSyncService.checkWriteLockPresent(resultTask.queuedTask.task.id);

        TaskImpl t = taskRepository.findById(resultTask.queuedTask.task.id).orElse(null);
        if (t==null) {
            log.warn("#317.180 Can't assign task #{}, task doesn't exist", resultTask.queuedTask.task.id);
            return null;
        }

        t.setAssignedOn(System.currentTimeMillis());
        t.setCoreId(coreId);
        t.setExecState(EnumsApi.TaskExecState.IN_PROGRESS.value);
        t.setResultResourceScheduledOn(0);

        taskRepository.save(t);

        eventPublisherService.publishUnAssignTaskTxEvent(new UnAssignTaskTxEvent(t.execContextId, t.id));
        eventPublisherService.publishStartTaskProcessingTxEvent(new StartTaskProcessingTxEvent(t.execContextId, t.id));

        eventPublisher.publishEvent(new PostTaskAssigningTxEvent(
                ()-> {
                    resultTask.assigned = true;
                    currentQuotas.addQuotas(new DispatcherData.AllocatedQuotas(t.id, resultTask.queuedTask.tag, quota.amount));
                }));
        eventPublisher.publishEvent(new PostTaskAssigningRollbackTxEvent(()-> resultTask.assigned = false));

        return new TaskData.AssignedTask(t, resultTask.queuedTask.tag, quota.amount);
    }

    @SuppressWarnings("MethodMayBeStatic")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleStartTaskProcessingTxEvent(PostTaskAssigningTxEvent event) {
        event.runnable.run();
    }

    @SuppressWarnings("MethodMayBeStatic")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleStartTaskProcessingRollbackTxEvent(PostTaskAssigningRollbackTxEvent event) {
        event.runnable.run();
    }

}

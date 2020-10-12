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

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.event.RegisterTaskForProcessingEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTaskStateService;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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

    private static final Object syncObj = new Object();

    @Async
    @EventListener
    public void registerTask(RegisterTaskForProcessingEvent event) {
        synchronized (syncObj) {
            taskProviderTransactionalService.registerTask(event.taskId);
        }
    }

    public int countOfTasks() {
        synchronized (syncObj) {
            return taskProviderTransactionalService.countOfTasks();
        }
    }

    @Nullable
    public TaskImpl findUnassignedTaskAndAssign(Long execContextId, Long processorId, ProcessorStatusYaml psy, boolean isAcceptOnlySigned) {
        TxUtils.checkTxNotExists();
        synchronized (syncObj) {
            TaskImpl task = taskProviderTransactionalService.findUnassignedTaskAndAssign(processorId, psy, isAcceptOnlySigned);
            if (task!=null) {
                execContextSyncService.getWithSyncNullable(execContextId,
                        ()->execContextTaskStateService.updateTaskExecStatesWithTx(execContextId, task.id, EnumsApi.TaskExecState.IN_PROGRESS, null));

                dispatcherEventService.publishTaskEvent(EnumsApi.DispatcherEventType.TASK_ASSIGNED, processorId, task.id, execContextId);
                taskProviderTransactionalService.deRegisterTask(task.id);
            }
            return task;
        }
    }


}

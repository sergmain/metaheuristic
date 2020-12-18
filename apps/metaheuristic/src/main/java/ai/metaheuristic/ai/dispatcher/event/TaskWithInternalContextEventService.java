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

package ai.metaheuristic.ai.dispatcher.event;

import ai.metaheuristic.ai.dispatcher.commons.DataHolder;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTaskFinishingService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskFinishingService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 3/15/2020
 * Time: 10:58 PM
 */
@SuppressWarnings("unused")
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("dispatcher")
public class TaskWithInternalContextEventService {

    private final TaskWithInternalContextService taskWithInternalContextService;
    private final ExecContextSyncService execContextSyncService;
    private final ExecContextCache execContextCache;
    private final ExecContextFSM execContextFSM;
    private final TaskRepository taskRepository;
    private final EventSenderService eventSenderService;
    private final ExecContextTaskFinishingService execContextTaskFinishingService;
    private final TaskFinishingService taskFinishingService;
    private final TaskSyncService taskSyncService;

    public void processInternalFunction(final TaskWithInternalContextEvent event) {
        TxUtils.checkTxNotExists();
        execContextSyncService.checkWriteLockNotPresent(event.execContextId);

        try {
            try (DataHolder holder = new DataHolder()) {
                execContextSyncService.getWithSyncNullable(event.execContextId,
                        () -> taskSyncService.getWithSyncNullable(event.taskId,
                                () -> taskWithInternalContextService.processInternalFunctionWithTx(event.execContextId, event.taskId, holder)));
                eventSenderService.sendEvents(holder);
            }
        } catch (Throwable th) {
            String es = "#989.020 Error while processing the task #"+event.taskId+" with internal function";
            log.error(es, th);
            execContextSyncService.getWithSyncNullable(event.execContextId,
                    () -> taskSyncService.getWithSyncNullable(event.taskId,
                            () -> taskFinishingService.finishWithErrorWithTx(event.taskId, es)));
        }
    }
}

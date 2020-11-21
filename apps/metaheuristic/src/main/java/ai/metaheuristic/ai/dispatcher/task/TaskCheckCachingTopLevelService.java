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

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.commons.DataHolder;
import ai.metaheuristic.ai.dispatcher.event.EventSenderService;
import ai.metaheuristic.ai.dispatcher.event.RegisterTaskForCheckCachingEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.exceptions.InvalidateCacheProcessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author Serge
 * Date: 10/30/2020
 * Time: 7:10 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskCheckCachingTopLevelService {

    private final ExecContextSyncService execContextSyncService;
    private final ExecContextService execContextService;
    private final TaskCheckCachingService taskCheckCachingService;
    private final EventSenderService eventSenderService;
    private final TaskSyncService taskSyncService;

//    private static final int N_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors() - 2);
    private static final int N_THREADS = 2;
    private final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(N_THREADS);

    public void checkCaching(final RegisterTaskForCheckCachingEvent event) {
        executor.submit(() -> {
            ExecContextImpl execContext = execContextService.findById(event.execContextId);
            if (execContext == null) {
                log.info("#610.020 ExecContext #{} doesn't exists", event.execContextId);
                return;
            }
            try {
                try (DataHolder holder = new DataHolder()) {
                    execContextSyncService.getWithSyncNullable(execContext.id,
                            () -> taskSyncService.getWithSyncNullable(event.taskId,
                                    () -> taskCheckCachingService.checkCaching(event.execContextId, event.taskId, holder)));
                    eventSenderService.sendEvents(holder);
                }
            } catch (InvalidateCacheProcessException e) {
                try {
                    execContextSyncService.getWithSyncNullable(execContext.id,
                            () -> taskSyncService.getWithSyncNullable(e.taskId,
                                    () -> taskCheckCachingService.invalidateCacheItemAndSetTaskToNone(e.execContextId, e.taskId, e.cacheProcessId)));
                } catch (Throwable th) {
                    log.error("#610.020 error while invalidating task #"+e.taskId, th);
                }
            }
        });
    }
}

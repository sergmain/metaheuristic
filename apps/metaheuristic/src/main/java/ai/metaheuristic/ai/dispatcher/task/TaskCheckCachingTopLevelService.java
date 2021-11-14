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

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.event.RegisterTaskForCheckCachingEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextReadinessStateService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.exceptions.InvalidateCacheProcessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
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

    private final ExecContextService execContextService;
    private final TaskCheckCachingService taskCheckCachingService;
    private final ExecContextReadinessStateService execContextReadinessStateService;

    private final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);

    private final LinkedList<RegisterTaskForCheckCachingEvent> queue = new LinkedList<>();

    public void putToQueue(final RegisterTaskForCheckCachingEvent event) {
        synchronized (queue) {
            if (queue.contains(event)) {
                return;
            }
            queue.add(event);
        }
    }

    @Nullable
    private RegisterTaskForCheckCachingEvent pullFromQueue() {
        synchronized (queue) {
            return queue.pollFirst();
        }
    }

    public void checkCaching() {
        final int activeCount = executor.getActiveCount();
//        log.info("checkCaching, active task in executor: {}", activeCount);
        if (activeCount>0) {
            return;
        }
        executor.submit(() -> {
            RegisterTaskForCheckCachingEvent event;
            while ((event = pullFromQueue())!=null) {
                checkCachingInternal(event);
            }
        });
    }

    private void checkCachingInternal(RegisterTaskForCheckCachingEvent event) {
        final boolean notReady = execContextReadinessStateService.isNotReady(event.execContextId);
//        log.info("execContextId: {}, notReady: {}", event.execContextId, notReady);
        if (notReady) {
            return;
        }

        ExecContextImpl execContext = execContextService.findById(event.execContextId);
        if (execContext == null) {
            return;
        }

        try {
            ExecContextSyncService.getWithSyncVoid(execContext.id,
                    () -> TaskSyncService.getWithSyncVoid(event.taskId,
                            () -> taskCheckCachingService.checkCaching(event.execContextId, event.taskId)));
        } catch (InvalidateCacheProcessException e) {
            log.error("#610.200 caught InvalidateCacheProcessException, {}", e.getMessage());
            try {
                ExecContextSyncService.getWithSyncVoid(execContext.id,
                        () -> TaskSyncService.getWithSyncVoid(e.taskId,
                                () -> taskCheckCachingService.invalidateCacheItemAndSetTaskToNone(e.execContextId, e.taskId, e.cacheProcessId)));
            } catch (Throwable th) {
                log.error("#610.300 error while invalidating task #"+e.taskId, th);
            }
        }
    }

}

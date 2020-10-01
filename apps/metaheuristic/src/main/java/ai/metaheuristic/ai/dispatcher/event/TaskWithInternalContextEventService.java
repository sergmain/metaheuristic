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

import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
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

    @Async
    @EventListener
    public void processInternalFunction(final TaskWithInternalContextEvent event) {
        log.info("#447.020 execContext #{}, {}", event.execContextId, execContextCache.findById(event.execContextId));
        TxUtils.checkTxNotExists();
        execContextSyncService.checkWriteLockNotPresent(event.execContextId);

        execContextSyncService.getWithSyncNullable(event.execContextId, () -> {
            log.info("#447.025 execContext #{}, {}", event.execContextId, execContextCache.findById(event.execContextId));
            taskWithInternalContextService.processInternalFunction(event);
            return null;
        });
    }

}

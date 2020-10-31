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
import ai.metaheuristic.ai.dispatcher.event.RegisterTaskForCheckCachingEvent;
import ai.metaheuristic.ai.dispatcher.event.RegisterTaskForProcessingEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public void checkCaching(RegisterTaskForCheckCachingEvent event) {
        TxUtils.checkTxNotExists();
        ExecContextImpl execContext = execContextService.findById(event.execContextId);
        if (execContext==null) {
            log.info("#609.020 ExecContext #{} doesn't exists", event.execContextId);
            return;
        }
        execContextSyncService.getWithSyncNullable(execContext.id,
                () -> taskCheckCachingService.checkCaching(event.execContextId, event.taskId));

    }
}

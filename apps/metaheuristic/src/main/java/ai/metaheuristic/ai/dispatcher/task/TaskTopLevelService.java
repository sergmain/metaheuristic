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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.event.ProcessDeletedExecContextEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

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

    private final ExecContextSyncService execContextSyncService;
    private final ExecContextCache execContextCache;
    private final TaskRepository taskRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TaskTransactionalService taskTransactionalService;

    public void deleteOrphanTasks(List<Long> orphanExecContextIds) {
        TxUtils.checkTxNotExists();
        for (Long execContextId : orphanExecContextIds) {
            if (execContextCache.findById(execContextId)!=null) {
                log.warn("execContextId #{} wasn't deleted, actually", execContextId);
                continue;
            }
            eventPublisher.publishEvent(new ProcessDeletedExecContextEvent(execContextId));

            List<Long> ids;
            while (!(ids = taskRepository.findAllByExecContextId(Consts.PAGE_REQUEST_100_REC, execContextId)).isEmpty()) {
                List<List<Long>> pages = CollectionUtils.parseAsPages(ids, 10);
                for (List<Long> page : pages) {
                    log.info("Found orphan task, execContextId: #{}, tasks #{}", execContextId, page);
                    execContextSyncService.getWithSyncNullable(execContextId, () -> taskTransactionalService.deleteOrphanTasks(page));
                }
            }
        }
    }


}

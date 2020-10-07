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

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;

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

    @Async
    @EventListener
    public void processInternalFunction(final TaskWithInternalContextEvent event) {
        log.info("#447.020 processInternalFunction(), thread #{}, task #{}, execContext #{}, {}",
                Thread.currentThread().getId(), event.taskId, event.execContextId, execContextCache.findById(event.execContextId));
        TxUtils.checkTxNotExists();
        execContextSyncService.checkWriteLockNotPresent(event.execContextId);

        if (true) throw new IllegalStateException("Not supported at this time");

        VariableData.DataStreamHolder holder = new VariableData.DataStreamHolder();
        try {
            execContextSyncService.getWithSyncNullable(event.execContextId, () -> {
                ExecContextImpl execContext = execContextCache.findById(event.execContextId);
                TaskImpl task;
                try {
                    task = taskRepository.findById(event.taskId).orElse(null);
                    if (task==null) {
                        log.warn("Task #{} with internal context doesn't exist", event.taskId);
                        return null;
                    }
                } catch (Throwable th) {
                    log.error("Error", th);
                    return null;
                }
                if (!event.execContextId.equals(task.execContextId)) {
                    log.error("The task #{} has different execContextId, expected: {}, actual: {}",
                            event.taskId, event.execContextId, task.execContextId);
                    execContextFSM.toError(execContext);
                    return null;
                }
                taskWithInternalContextService.processInternalFunction(execContext, task, holder);
                return null;
            });
        }
        finally {
            for (InputStream inputStream : holder.inputStreams) {
                try {
                    inputStream.close();
                }
                catch(Throwable th)  {
                    log.warn("Error while closing stream", th);
                }
            }
        }
    }

}

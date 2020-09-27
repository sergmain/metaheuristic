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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.locks.ReentrantReadWriteLock;

@SuppressWarnings("DuplicatedCode")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskPersistencer {

    private final TaskRepository taskRepository;
    private final TaskSyncService taskSyncService;
    private final ExecContextSyncService execContextSyncService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TaskImpl save(TaskImpl task) {
        if (task.id!=null) {
            ReentrantReadWriteLock.WriteLock lock = taskSyncService.getWriteLock(task.id);
            if (!lock.isHeldByCurrentThread()) {
                if (!execContextSyncService.getWriteLock(task.execContextId).isHeldByCurrentThread()) {
                    try {
                        throw new RuntimeException("The thread isn't locked by execContextSyncService or taskSyncService");
                    }
                    catch (RuntimeException e) {
                        log.error("The thread isn't locked by execContextSyncService or taskSyncService", e);
                    }
                }
            }
        }
        try {
            throw new RuntimeException("stacktrace");
        }
        catch (RuntimeException e) {
            log.info("stacktrace for task #"+task.id+", version: "+task.version, e);
        }
        return taskRepository.save(task);
    }

}

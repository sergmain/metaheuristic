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
package ai.metaheuristic.ai.dispatcher;

import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class ArtifactCleanerAtDispatcher {

    private final ExecContextRepository execContextRepository;
    private final TaskRepository taskRepository;
    private final VariableRepository variableRepository;

    public void fixedDelay() {
        deleteOrphanTasks();
        deleteOrphanExecContextData();
    }

    private void deleteOrphanExecContextData() {
        deleteOrphanData(variableRepository.findAllOrphanExecContextData());
    }

    private void deleteOrphanData(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }

        // lets delete no more than 1000 record per call of ai.metaheuristic.ai.launchpad.ArtifactCleanerAtLaunchpad.deleteOrphanData()
        for (int i = 0; i < Math.min(ids.size(), 1000); i++) {
            variableRepository.deleteById(ids.get(i));
        }
    }

    private void deleteOrphanTasks() {
        List<Long> ids = execContextRepository.findAllIds();;
        int page = 0;
        final AtomicBoolean isFound = new AtomicBoolean();
        do {
            isFound.set(false);
            taskRepository.findAllAsTaskSimple(PageRequest.of(page, 100))
                    .forEach(t -> {
                        isFound.set(true);
                        Long execContextId = (Long) t[1];
                        if (!ids.contains(execContextId)) {
                            log.info("Found orphan task #{}, execContextId: #{}", t[0], execContextId);
                            taskRepository.deleteById((Long) t[0]);
                        }
                    });
            page++;
        } while (isFound.get());
    }

    
}

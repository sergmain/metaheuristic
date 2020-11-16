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
package ai.metaheuristic.ai.dispatcher.commons;

import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskTopLevelService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTopLevelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class ArtifactCleanerAtDispatcher {

    private final TaskRepository taskRepository;
    private final TaskTopLevelService taskTopLevelService;
    private final VariableRepository variableRepository;
    private final VariableTopLevelService variableTopLevelService;
    private final ExecContextTopLevelService execContextTopLevelService;
    private final ExecContextRepository execContextRepository;

    public void fixedDelay() {
        deleteOrphanExecContexts();
        deleteOrphanTasks();
        deleteOrphanVariables();
    }

    private void deleteOrphanExecContexts() {
        execContextTopLevelService.deleteOrphanExecContexts(execContextRepository.findAllIdsForOrphanExecContexts());
    }

    private void deleteOrphanTasks() {
        taskTopLevelService.deleteOrphanTasks(taskRepository.findAllExecContextIdsForOrphanTasks());
    }

    private void deleteOrphanVariables() {
        variableTopLevelService.deleteOrphanVariables(variableRepository.findAllExecContextIdsForOrphanVariables());
    }


}

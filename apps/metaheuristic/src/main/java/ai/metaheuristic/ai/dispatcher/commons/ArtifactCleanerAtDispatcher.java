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
package ai.metaheuristic.ai.dispatcher.commons;

import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.task.TaskTopLevelService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTopLevelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final SourceCodeCache sourceCodeCache;

    public void fixedDelay() {
        // !!! DO NOT CHANGE THE ORDER OF CALLING !!!
        deleteOrphanExecContexts();
        deleteOrphanTasks();
        deleteOrphanVariables();
    }

    private void deleteOrphanExecContexts() {
        Set<Long> forDeletion = new HashSet<>();
        List<Object[]> objs = execContextRepository.findAllExecContextIdWithSourceCodeId();
        for (Object[] obj : objs) {
            long sourceCodeId = ((Number)obj[1]).longValue();
            if (sourceCodeCache.findById(sourceCodeId)==null) {
                long execContextId = ((Number)obj[0]).longValue();
                forDeletion.add(execContextId);
            }
        }
        execContextTopLevelService.deleteOrphanExecContexts(forDeletion);
    }

    private void deleteOrphanTasks() {
        taskTopLevelService.deleteOrphanTasks(taskRepository.findAllExecContextIdsForOrphanTasks());
    }

    private void deleteOrphanVariables() {
        variableTopLevelService.deleteOrphanVariables(variableRepository.findAllExecContextIdsForOrphanVariables());
    }


}

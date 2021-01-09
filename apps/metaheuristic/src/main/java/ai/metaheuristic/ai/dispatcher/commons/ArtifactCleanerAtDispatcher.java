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

import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.*;
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
    private final BatchRepository batchRepository;
    private final CompanyRepository companyRepository;
    private final BatchTopLevelService batchTopLevelService;

    public void fixedDelay() {
        // do not change the order of calling
        deleteOrphanBatches();
        deleteOrphanExecContexts();
        deleteOrphanTasks();
        deleteOrphanVariables();
    }

    private void deleteOrphanBatches() {
        List<Long> execContextIds = execContextRepository.findAllIds();
        List<Long> companyUniqueIds = companyRepository.findAllUniqueIds();
        Set<Long> forDeletion = new HashSet<>();
        List<Object[]> objs = batchRepository.findAllBatchedShort();
        for (Object[] obj : objs) {
            // id, b.execContextId, b.companyId
            Long execContextId = ((Number)obj[1]).longValue();
            Long companyUniqueId = ((Number)obj[2]).longValue();
            if (!execContextIds.contains(execContextId) || !companyUniqueIds.contains(companyUniqueId)) {
                Long batchId = ((Number)obj[0]).longValue();
                forDeletion.add(batchId);
            }
        }
        batchTopLevelService.deleteOrphanBatches(forDeletion);
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

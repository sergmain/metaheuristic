/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.preparing;

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSchedulerService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTaskResettingTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateService;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateTopLevelService;
import ai.metaheuristic.ai.dispatcher.internal_functions.TaskWithInternalContextEventService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepositoryForTest;
import ai.metaheuristic.ai.dispatcher.task.TaskCheckCachingService;
import ai.metaheuristic.ai.dispatcher.task.TaskFinishingTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.dispatcher.task.TaskVariableTopLevelService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Sergio Lissner
 * Date: 6/3/2026
 * Time: 7:11 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class MhInternalTaskPipelineRunnerTxService {

    private final TaskRepository taskRepository;
    private final TaskRepositoryForTest taskRepositoryForTest;
    private final ExecContextCache execContextCache;
    private final ExecContextSchedulerService execContextSchedulerService;
    private final TaskWithInternalContextEventService taskWithInternalContextEventService;
    private final ExecContextTaskStateService execContextTaskStateService;
    private final ExecContextVariableStateTopLevelService execContextVariableStateTopLevelService;
    private final TaskCheckCachingService taskCheckCachingService;
    private final TaskFinishingTopLevelService taskFinishingTopLevelService;
    private final TaskVariableTopLevelService taskVariableTopLevelService;
    private final TxSupportForTestingService txSupportForTestingService;
    private final ExecContextFSM execContextFSM;
    private final ExecContextTaskResettingTopLevelService execContextTaskResettingTopLevelService;

    @Transactional
    public void transitionToInProgress(Long taskId) {
        // Transition task to IN_PROGRESS (simulates Processor picking it up).
        TaskImpl t = taskRepository.findById(taskId).orElse(null);
        assertNotNull(t);
        t.setExecState(EnumsApi.TaskExecState.IN_PROGRESS.value);
        t.setAssignedOn(System.currentTimeMillis());
        taskRepository.save(t);
    }
}

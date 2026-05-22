/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.internal_function.evaluation;

import ai.metaheuristic.ai.dispatcher.event.events.InitVariablesEvent;
import ai.metaheuristic.ai.dispatcher.event.events.UpdateTaskExecStatesInExecContextEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextStatusService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTxService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepositoryForTest;
import ai.metaheuristic.ai.dispatcher.task.TaskFinishingTxService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.dispatcher.task.TaskVariableInitTxService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.commons.exceptions.CommonRollbackException;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class TestBaseEvaluation extends PreparingSourceCode {

    @Autowired public TxSupportForTestingService txSupportForTestingService;
    @Autowired public TaskRepositoryForTest taskRepositoryForTest;
    @Autowired public ExecContextTxService execContextTxService;
    @Autowired public ExecContextStatusService execContextStatusService;
    @Autowired public ExecContextTaskStateService execContextTaskStateTopLevelService;
    @Autowired public ExecContextGraphTopLevelService execContextGraphTopLevelService;
    @Autowired public ExecContextRepository execContextRepository;
    @Autowired public PreparingSourceCodeService preparingSourceCodeService;
    @Autowired public TaskVariableInitTxService taskVariableInitTxService;
    @Autowired public ExecContextTaskStateService execContextTaskStateService;
    @Autowired public TaskFinishingTxService taskFinishingTxService;
    @Autowired public VariableService variableService;
    @Autowired public VariableTxService variableTxService;

    @AfterEach
    public void afterTestSourceCodeService() {
        System.out.println("Finished TestSourceCodeService.afterTestSourceCodeService()");
        if (getExecContextForTest() !=null) {
            ExecContextSyncService.getWithSyncNullable(getExecContextForTest().id,
                    () -> txSupportForTestingService.deleteByExecContextId(getExecContextForTest().id));
        }
    }

    public Long initVariableEvents() {
        // The graph state (ExecContextTaskState) is populated by an @Async
        // @TransactionalEventListener(AFTER_COMMIT) chain fired after
        // produceAndStartAllTasks commits or after a task finishes — so when
        // this method is called immediately after a task lifecycle event, the
        // unfinished-task list may still be empty. Wait for it to surface
        // before iterating; otherwise the loop never runs and forUpdating
        // stays null.
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> !getUnfinishedTaskVertices(getExecContextForTest()).isEmpty());

        // Production wiring also fires an @Async @EventListener
        // (TaskVariableInitService.handleEvent) on InitVariablesEvent which
        // transitions an INIT task to NONE via the same intiVariables call we
        // make below. By the time the test thread arrives the task is usually
        // already in NONE state, so taskVariableInitTxService.intiVariables
        // throws CommonRollbackException — that's expected, not a failure: the
        // variables were initialized for us. Treat "successfully initialized"
        // and "already initialized (CommonRollbackException)" as equivalent
        // success outcomes and pick the first such task as the active one.
        List<Long> taskIds = getUnfinishedTaskVertices(getExecContextForTest());
        Long forUpdating = null;
        for (Long taskId : taskIds) {
            try {
                TaskSyncService.getWithSyncVoid(taskId, ()-> taskVariableInitTxService.intiVariables(new InitVariablesEvent(taskId)));
                forUpdating = taskId;
                break;
            } catch (CommonRollbackException e) {
                // Already initialized by the async listener — this task is still
                // the active unfinished one and is now in NONE state, ready to be
                // picked up by the internal-task queue.
                forUpdating = taskId;
                break;
            }
        }
        assertNotNull(forUpdating);
        execContextTaskStateService.updateTaskExecStatesExecContext(new UpdateTaskExecStatesInExecContextEvent(getExecContextForTest().id, List.of(forUpdating)));
        return forUpdating;
    }

}

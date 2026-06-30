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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.event.events.InitVariablesEvent;
import ai.metaheuristic.ai.exceptions.TaskCreationException;
import ai.metaheuristic.ai.exceptions.VariableImmutabilityException;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.exceptions.CommonRollbackException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Input-variable initialization runs OUTSIDE the Function life-cycle (before any
 * Processor ever runs the task's function). When a declared input variable can't be
 * resolved, {@code TaskVariableInitTxService.toInputVariable()} throws
 * {@link TaskCreationException}. This guards the exception-handling contract of
 * {@link TaskVariableInitService#intiVariables}.
 *
 * Pure unit test: the two collaborators are hand-rolled subclasses (no mockito/assertj),
 * constructed with null deps because only their overridden methods are exercised.
 *
 * @author Sergio Lissner
 */
@Execution(ExecutionMode.CONCURRENT)
public class TaskVariableInitServiceErrorRecoveryTest {

    /** Stands in for the @Transactional tx-service; throws on demand from intiVariables(). */
    private static class ThrowingTaskVariableInitTxService extends TaskVariableInitTxService {
        private final RuntimeException toThrow;
        int calls = 0;
        ThrowingTaskVariableInitTxService(RuntimeException toThrow) {
            super(null, null, null, null, null, null, null, null);
            this.toThrow = toThrow;
        }
        @Override
        public void intiVariables(InitVariablesEvent event, Long execContextGraphId, ExecContextParamsYaml execContextParamsYaml) {
            calls++;
            if (toThrow != null) {
                throw toThrow;
            }
        }
    }

    /** Records how the task was finished: ERROR_WITH_RECOVERY (2-arg) vs explicit state (3-arg). */
    private static class RecordingTaskFinishingTxService extends TaskFinishingTxService {
        int recoveryCalls = 0;
        Long recoveryTaskId = null;
        int erroredCalls = 0;
        EnumsApi.TaskExecState erroredState = null;
        RecordingTaskFinishingTxService() {
            super(null, null, null, null, null, null, null, null, null);
        }
        @Override
        public void finishWithErrorWithTx(Long taskId, String console) {
            recoveryCalls++;
            recoveryTaskId = taskId;
        }
        @Override
        public void finishWithErrorWithTx(Long taskId, String console, EnumsApi.TaskExecState targetState) {
            erroredCalls++;
            erroredState = targetState;
        }
    }

    /**
     * Builds the service under test. The ExecContext is now resolved OUTSIDE the @Transactional
     * boundary inside TaskVariableInitService; this stub returns a minimal ExecContext so the flow
     * reaches the (throwing) tx-service, keeping the exception-routing contract under test.
     */
    private static TaskVariableInitService newService(TaskVariableInitTxService tx, TaskFinishingTxService finishing) {
        return new TaskVariableInitService(tx, finishing, null, null) {
            @Override
            ExecContextImpl resolveExecContext(Long taskId) {
                ExecContextImpl ec = new ExecContextImpl();
                ec.execContextGraphId = 1L;
                ec.updateParams(new ExecContextParamsYaml());
                return ec;
            }
        };
    }

    /**
     * CHARACTERIZATION of the current (buggy) behavior: a TaskCreationException raised while
     * initializing input variables ESCAPES intiVariables() uncaught. It is then swallowed by
     * MultiTenantedQueue (logged as "Error"), the task stays in INIT, and reconciliation
     * re-fires InitVariablesEvent forever. No transition to ERROR_WITH_RECOVERY happens.
     */
    @Test
    public void test_taskCreationException_routesToErrorWithRecovery() {
        final long taskId = 9125L;
        ThrowingTaskVariableInitTxService txFake = new ThrowingTaskVariableInitTxService(
                new TaskCreationException("179.120 (variable==null), name: topLevelReqCount, variableContext: local, taskContextId: 1, execContextId: 156"));
        RecordingTaskFinishingTxService finishingFake = new RecordingTaskFinishingTxService();
        TaskVariableInitService service = newService(txFake, finishingFake);
        var ec = service.resolveExecContext(taskId);
        assertNotNull(ec);
        InitVariablesEvent event = new InitVariablesEvent(ec.id, taskId);

        assertDoesNotThrow(() -> service.intiVariables(event));
        assertEquals(1, finishingFake.recoveryCalls, "TaskCreationException during input-variable init must be finished as ERROR_WITH_RECOVERY");
        assertEquals(taskId, finishingFake.recoveryTaskId);
        assertEquals(0, finishingFake.erroredCalls, "must not jump straight to ERROR (that is the immutability path)");
    }

    @Test
    public void test_commonRollbackException_isSwallowedSilently() {
        ThrowingTaskVariableInitTxService txFake = new ThrowingTaskVariableInitTxService(new CommonRollbackException());
        RecordingTaskFinishingTxService finishingFake = new RecordingTaskFinishingTxService();
        TaskVariableInitService service = newService(txFake, finishingFake);
        long taskId = 42L;
        var ec = service.resolveExecContext(taskId);
        assertNotNull(ec);

        assertDoesNotThrow(() -> service.intiVariables(new InitVariablesEvent(ec.id, taskId)));
        assertEquals(0, finishingFake.recoveryCalls);
        assertEquals(0, finishingFake.erroredCalls);
    }

    @Test
    public void test_variableImmutabilityException_routesToError() {
        ThrowingTaskVariableInitTxService txFake = new ThrowingTaskVariableInitTxService(
                new VariableImmutabilityException("immutability violation", "topLevelReqs", "1", "1#2"));
        RecordingTaskFinishingTxService finishingFake = new RecordingTaskFinishingTxService();
        TaskVariableInitService service = newService(txFake, finishingFake);
        long taskId = 7L;
        var ec = service.resolveExecContext(taskId);
        assertNotNull(ec);

        assertDoesNotThrow(() -> service.intiVariables(new InitVariablesEvent(ec.id, taskId)));
        assertEquals(1, finishingFake.erroredCalls);
        assertEquals(EnumsApi.TaskExecState.ERROR, finishingFake.erroredState);
        assertEquals(0, finishingFake.recoveryCalls);
    }

    @Test
    public void test_successfulInit_doesNotTouchFinishingService() {
        ThrowingTaskVariableInitTxService txFake = new ThrowingTaskVariableInitTxService(null);
        RecordingTaskFinishingTxService finishingFake = new RecordingTaskFinishingTxService();
        TaskVariableInitService service = newService(txFake, finishingFake);

        long taskId = 100L;
        var ec = service.resolveExecContext(taskId);
        assertNotNull(ec);
        assertDoesNotThrow(() -> service.intiVariables(new InitVariablesEvent(ec.id, taskId)));
        assertEquals(1, txFake.calls);
        assertEquals(0, finishingFake.recoveryCalls);
        assertEquals(0, finishingFake.erroredCalls);
    }
}

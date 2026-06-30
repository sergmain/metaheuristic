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

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.event.events.InitVariablesEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextStatusService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepositoryForTest;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.api.EnumsApi;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the routing contract of {@link TaskVariableInitService#intiVariables} — the orchestration
 * that resolves the ExecContext OUTSIDE the @Transactional boundary and routes the tx-service's
 * outcome:
 * <ul>
 *   <li>success: a freshly produced INIT task gets its variables initialized and leaves INIT;</li>
 *   <li>{@code CommonRollbackException} (task already past INIT / no init phase) is swallowed
 *       silently — the task is NOT finished with an error;</li>
 *   <li>{@code TaskCreationException} / {@code VariableImmutabilityException} are routed to the
 *       error-finishing path (covered by the integration error tests; not force-injected here).</li>
 * </ul>
 *
 * This runs on the shared V3 context (PreparingSourceCode harness) against a REAL ExecContext and
 * REAL tasks — no Mockito, no hand-rolled service subclasses. Tasks are produced WITHOUT starting
 * the ExecContext so the scheduler does not advance them past variable-init, keeping the routing
 * assertions deterministic.
 *
 * @author Sergio Lissner
 */
@SuppressWarnings("unused")
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class TaskVariableInitServiceErrorRecoveryTest extends PreparingSourceCode {

    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private ExecContextStatusService execContextStatusService;
    @Autowired private TaskRepositoryForTest taskRepositoryForTest;
    @Autowired private TaskVariableInitService taskVariableInitService;

    @SneakyThrows
    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/test-evaluation/test-evaluation-1.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @Test
    public void test_intiVariables_initializesRealTasks_andRoutesWithoutSpuriousError() {
        preparingSourceCodeService.produceTasksForTestWithoutStarting(resolveSourceCode(getSourceCodeAndLang()), preparingSourceCodeData);

        final var ec = getExecContextForTest();

        // the graph state (unfinished vertices) is populated by an @Async @TransactionalEventListener
        // AFTER_COMMIT chain, so wait for it to surface before iterating.
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
                .until(() -> !getUnfinishedTaskVertices(ec).isEmpty());

        final List<Long> taskIds = getUnfinishedTaskVertices(ec);

        // Drive variable-init through the SERVICE (the routing layer) for every unfinished task.
        // Then drive a second pass: every task is now past INIT, so the tx-service throws
        // CommonRollbackException which the service must swallow silently (idempotent no-op).
        for (Long taskId : taskIds) {
            taskVariableInitService.intiVariables(new InitVariablesEvent(ec.id, taskId));
        }
        for (Long taskId : taskIds) {
            taskVariableInitService.intiVariables(new InitVariablesEvent(ec.id, taskId));
        }

        final List<TaskImpl> tasks = taskRepositoryForTest.findByExecContextIdAsList(ec.id);
        assertFalse(tasks.isEmpty());

        // The routing must never finish a normal task with an error state (the success and the
        // swallowed-rollback paths both leave the task non-error).
        for (TaskImpl t : tasks) {
            assertNotEquals(EnumsApi.TaskExecState.ERROR.value, t.execState,
                    "intiVariables must not route a normal task to ERROR");
            assertNotEquals(EnumsApi.TaskExecState.ERROR_WITH_RECOVERY.value, t.execState,
                    "intiVariables must not route a normal task to ERROR_WITH_RECOVERY");
        }

        // And at least one task must actually have been initialized (INIT -> NONE) by the service.
        final boolean anyInitialized = tasks.stream().anyMatch(t -> t.execState == EnumsApi.TaskExecState.NONE.value);
        assertTrue(anyInitialized, "at least one task must be initialized to NONE by intiVariables");
    }

}

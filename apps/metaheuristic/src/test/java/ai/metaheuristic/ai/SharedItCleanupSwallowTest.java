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

package ai.metaheuristic.ai;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.api.EnumsApi;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization test for the per-test cleanup in {@link MhSharedItTest}.
 *
 * <p>{@code stopNonFinishedExecContexts()} reaches {@code ExecContextSyncService.getWithSync},
 * which calls {@code TxUtils.checkTxNotExists()}. When the cleanup runs with a transaction already
 * bound to the thread - which is what a {@code @Transactional} subclass produces, because Spring's
 * TransactionalTestExecutionListener ends the test transaction AFTER {@code @AfterEach} - the call
 * throws {@code IllegalStateException: Tx exists}.
 *
 * <p>That throw is caught and logged inside {@code stopNonFinishedExecContexts()}, so the leftover
 * ExecContext is never stopped and the test still reports green. In a full MH suite run this
 * happens 22 times in TestFindVariableInAllInternalContexts alone, and every one of those tests
 * passes.
 *
 * <p>This test drives the same path directly: it commits a STARTED ExecContext, then invokes the
 * cleanup from inside an explicit transaction.
 *
 * @author Sergio Lissner
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
@Slf4j
public class SharedItCleanupSwallowTest extends MhSharedItTest {

    @Autowired private PlatformTransactionManager txManager;
    @Autowired private ExecContextCache execContextCache;

    @Test
    public void test_cleanupFailureInsideTx_isSwallowedAndExecContextStaysStarted() {
        final TransactionTemplate tt = new TransactionTemplate(txManager);

        final Long ecId = tt.execute(status -> createStartedExecContext());
        assertNotNull(ecId, "SETUP: ExecContext must have been created");
        assertEquals(EnumsApi.ExecContextState.STARTED.code, requireEc(ecId).state,
            "SETUP: ExecContext #" + ecId + " must start out as STARTED");

        // the cleanup the base class runs in @AfterEach, invoked with a tx bound to this thread
        final IllegalStateException actual = assertThrows(IllegalStateException.class,
            () -> tt.execute(status -> {
                resetSharedItStatePerTest();
                return null;
            }),
            "a per-test cleanup that could not run must fail the test instead of being logged and "
            + "swallowed; ExecContext #" + ecId + " was left STARTED and the test still reported green");

        assertTrue(actual.getMessage().contains("Tx exists"),
            "the propagated failure must be the 'Tx exists' one, actual: " + actual.getMessage());

        finishExecContext(tt, ecId);
    }

    private Long createStartedExecContext() {
        final ExecContextImpl ec = new ExecContextImpl();
        ec.sourceCodeId = 2L;
        ec.companyId = 42L;
        ec.accountId = 2L;
        ec.createdOn = System.currentTimeMillis();
        ec.state = EnumsApi.ExecContextState.STARTED.code;
        ec.execContextVariableStateId = 0L;
        ec.execContextGraphId = 0L;
        ec.execContextTaskStateId = 0L;
        ec.setParams("version: 1\nprocesses: []\nvariables:\n  inline: {}\n  inputs: []\n  outputs: []\n");
        return execContextCache.save(ec).id;
    }

    private ExecContextImpl requireEc(Long ecId) {
        final ExecContextImpl ec = execContextCache.findById(ecId, true);
        assertNotNull(ec, "ExecContext #" + ecId + " must be readable");
        return ec;
    }

    /** Leave nothing STARTED behind for the scheduler or for the base class's own @AfterEach. */
    private void finishExecContext(TransactionTemplate tt, Long ecId) {
        ExecContextSyncService.getWithSync(ecId, () -> tt.execute(status -> {
            final ExecContextImpl ec = execContextCache.findById(ecId);
            if (ec != null) {
                ec.state = EnumsApi.ExecContextState.FINISHED.code;
                execContextCache.save(ec);
            }
            return Boolean.TRUE;
        }));
    }
}

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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.signal_bus.ScopeRef;
import ai.metaheuristic.ai.dispatcher.signal_bus.SignalBus;
import ai.metaheuristic.ai.dispatcher.signal_bus.SignalEntry;
import ai.metaheuristic.ai.dispatcher.signal_bus.SignalKind;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.EnumsApi;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the Start / Stop buttons announce when the ExecContext is ALREADY in the requested state.
 *
 * <p>{@code changeExecContextState} guards the whole body — the DB write and the signal alike — with
 * {@code if (execContext.state != execState.code)}. Skipping the write is right; skipping the
 * announcement is not. The signal carries the ExecContext's CURRENT state, not a transition, so a
 * request that found it already in the target state still has something true to say, and saying it
 * is the only way a consumer holding a stale entry can be brought back into agreement.
 *
 * <p>ExecContext #293 is the case that made this matter: the DB held STARTED, a consumer held an
 * older STOPPED, and pressing Start was a no-op that published nothing — so the disagreement had no
 * way to resolve, however many times the button was pressed.
 *
 * <p>The observable here is the real {@link SignalBus} and its real revision counter, not a double.
 * The whole publish path is synchronous — {@code @TransactionalEventListener(AFTER_COMMIT)} on
 * {@code SignalBusTxBridge}, then a plain {@code @EventListener} on {@code SignalBusListener} — so by
 * the time the call returns the entry is either there or it is not; nothing needs awaiting. And
 * EXEC_CONTEXT carries {@code CoalescePolicy.NONE}, so every publish bumps the revision and two
 * publishes in quick succession cannot be folded into one.
 *
 * @author Sergio Lissner
 * Date: 9/20/2026
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
@Slf4j
public class ExecContextTargetStateSignalTest extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private ExecContextFSM execContextFSM;
    @Autowired private SignalBus signalBus;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/default-source-code-for-testing.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @Test
    public void test_pressingStartOnAnAlreadyStartedExecContextStillAnnouncesItsState() {
        DispatcherContext context = new DispatcherContext(getAccount(), getCompany());
        ExecContextCreatorService.ExecContextCreationResult result =
                txSupportForTestingService.createExecContext(getSourceCode(), context.asUserExecContext());
        setExecContextForTest(result.execContext);
        assertNotNull(getExecContextForTest());

        final Long execContextId = getExecContextForTest().id;

        // ---- a real transition: NONE -> STARTED --------------------------------------------------
        changeState(EnumsApi.ExecContextState.STARTED);

        final long afterRealTransition = signalRevision(execContextId);
        assertTrue(afterRealTransition > 0L,
                "setup failed: a real state transition must publish an EXEC_CONTEXT signal");
        assertEquals(EnumsApi.ExecContextState.STARTED.name(), signalStateName(execContextId),
                "setup failed: the published signal must carry the state that was reached");

        refreshExecContext();
        assertEquals(EnumsApi.ExecContextState.STARTED.code, getExecContextForTest().state,
                "setup failed: the ExecContext must be STARTED before the no-op request is made");

        // ---- the act: press Start again on an ExecContext that is already STARTED ----------------
        changeState(EnumsApi.ExecContextState.STARTED);

        // ---- the assertion the bug lives in ------------------------------------------------------
        assertTrue(signalRevision(execContextId) > afterRealTransition,
                "an explicit request must announce the state it leaves the ExecContext in, even when it was "
                        + "already there - that announcement is the only way a stale consumer gets corrected");
        assertEquals(EnumsApi.ExecContextState.STARTED.name(), signalStateName(execContextId),
                "the announcement must carry the state the ExecContext is actually in");

        refreshExecContext();
        assertEquals(EnumsApi.ExecContextState.STARTED.code, getExecContextForTest().state,
                "the no-op request must leave the stored state alone");
    }

    // ---- helpers ------------------------------------------------------------------------------

    private void changeState(EnumsApi.ExecContextState state) {
        final Long execContextId = getExecContextForTest().id;
        ExecContextSyncService.getWithSyncVoid(execContextId,
                () -> execContextFSM.changeExecContextStateWithTx(state, execContextId, getCompany().uniqueId));
    }

    /** Highest revision the real bus currently holds for this ExecContext, or 0 when it holds none. */
    private long signalRevision(Long execContextId) {
        return entryFor(execContextId).map(SignalEntry::revision).orElse(0L);
    }

    private String signalStateName(Long execContextId) {
        return entryFor(execContextId)
                .map(e -> String.valueOf(e.info().get("stateName")))
                .orElseThrow(() -> new IllegalStateException("no EXEC_CONTEXT signal for execContext #" + execContextId));
    }

    private Optional<SignalEntry> entryFor(Long execContextId) {
        refreshExecContext();
        return signalBus.query(new ScopeRef(getExecContextForTest().companyId), 0L,
                        Set.of(SignalKind.EXEC_CONTEXT), List.of())
                .signals().stream()
                .filter(e -> String.valueOf(execContextId).equals(e.signalId()))
                .findFirst();
    }

    private void refreshExecContext() {
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id, true)));
    }
}

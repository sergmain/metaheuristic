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

package ai.metaheuristic.ai.dispatcher.execution_gate;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.MhSharedItTest;
import ai.metaheuristic.ai.SharedItEnv;
import ai.metaheuristic.ai.dispatcher.beans.ExecutionGate;
import ai.metaheuristic.ai.dispatcher.repositories.ExecutionGateRepository;
import ai.metaheuristic.ai.yaml.execution_gate.ExecutionGateParamsYaml;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MH_EXECUTION_GATE round-trips, and its unique index actually exists.
 *
 * <p>The index assertion is the one worth having. It is the entire idempotency mechanism — opening a
 * block on a key that already has one must extend the existing row rather than stack a second — and
 * an index that silently failed to be created would not break anything visibly until two rows for
 * one key produced two different deadlines and the shorter one won.
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
public class ExecutionGatePersistenceTest extends MhSharedItTest {

    @Autowired private ExecutionGateRepository executionGateRepository;

    private final List<Long> created = new ArrayList<>();

    @AfterEach
    public void deleteCreatedRows() {
        // the DB is shared for the whole run, so anything this class writes has to leave with it
        for (Long id : created) {
            executionGateRepository.deleteById(id);
        }
        created.clear();
    }

    @Test
    public void test_rowRoundTripsWithAPopulatedParamsDocument() {
        final String refKey = SharedItEnv.uniqueCode("gate-round-trip");
        final long blockedUntil = System.currentTimeMillis() + 20 * 60 * 1_000L;

        final ExecutionGate saved = save(EnumsApi.GateScope.function, refKey, blockedUntil, "downtime", egpy -> {
            egpy.triggeredByTaskId = 424_242L;
            egpy.functionCode = "some-function:1.1";
            egpy.processorId = 17L;
            egpy.matchedPattern = "(?i)rate.limit";
            egpy.consoleExcerpt = "rate limit reached, retry later";
            egpy.incrementTries = false;
        });

        final ExecutionGate reRead = executionGateRepository.findById(saved.id).orElse(null);
        assertNotNull(reRead);

        assertEquals(EnumsApi.GateScope.function.name(), reRead.scope);
        assertEquals(refKey, reRead.refKey);
        assertEquals(blockedUntil, reRead.blockedUntil);
        assertEquals("downtime", reRead.reasonCode);
        assertNotNull(reRead.version);

        final ExecutionGateParamsYaml egpy = reRead.getExecutionGateParamsYaml();
        assertEquals(424_242L, egpy.triggeredByTaskId);
        assertEquals("some-function:1.1", egpy.functionCode);
        assertEquals(17L, egpy.processorId);
        assertEquals("(?i)rate.limit", egpy.matchedPattern);
        assertEquals("rate limit reached, retry later", egpy.consoleExcerpt);
        assertFalse(egpy.incrementTries);
    }

    @Test
    public void test_secondRowOnTheSameScopeAndRefKeyIsRejectedByTheUniqueIndex() {
        final String refKey = SharedItEnv.uniqueCode("gate-duplicate");

        save(EnumsApi.GateScope.api, refKey, System.currentTimeMillis() + 60_000L, "downtime", egpy -> {});

        assertThrows(DataIntegrityViolationException.class,
                () -> save(EnumsApi.GateScope.api, refKey, System.currentTimeMillis() + 120_000L, "downtime", egpy -> {}),
                "a second row on the same (SCOPE, REF_KEY) must be refused - the unique index is what makes re-blocking extend rather than stack");
    }

    @Test
    public void test_theSameRefKeyUnderADifferentScopeIsAllowed() {
        // the index is on the PAIR: one Function code and one processor id could collide as strings
        // without meaning the same thing
        final String refKey = SharedItEnv.uniqueCode("gate-cross-scope");

        final ExecutionGate asFunction = save(EnumsApi.GateScope.function, refKey, System.currentTimeMillis() + 60_000L, "downtime", egpy -> {});
        final ExecutionGate asProcessor = save(EnumsApi.GateScope.processor, refKey, System.currentTimeMillis() + 60_000L, "downtime", egpy -> {});

        assertNotEquals(asFunction.id, asProcessor.id);
        assertNotNull(executionGateRepository.findByScopeAndRefKey(EnumsApi.GateScope.function.name(), refKey));
        assertNotNull(executionGateRepository.findByScopeAndRefKey(EnumsApi.GateScope.processor.name(), refKey));
    }

    @Test
    public void test_liveAndExpiredRowsAreSeparatedByTheDeadline() {
        final long now = System.currentTimeMillis();
        final String liveKey = SharedItEnv.uniqueCode("gate-live");
        final String expiredKey = SharedItEnv.uniqueCode("gate-expired");

        final ExecutionGate live = save(EnumsApi.GateScope.company, liveKey, now + 600_000L, "manual", egpy -> {});
        final ExecutionGate expired = save(EnumsApi.GateScope.company, expiredKey, now - 600_000L, "manual", egpy -> {});

        final List<Long> liveIds = executionGateRepository.findAllLive(now).stream().map(g -> g.id).toList();
        final List<Long> expiredIds = executionGateRepository.findExpiredIds(now);

        // the shared DB holds other classes' rows too, so assert membership rather than the whole set
        assertTrue(liveIds.contains(live.id));
        assertFalse(liveIds.contains(expired.id));
        assertTrue(expiredIds.contains(expired.id));
        assertFalse(expiredIds.contains(live.id));
    }

    private ExecutionGate save(EnumsApi.GateScope scope, String refKey, long blockedUntil, String reasonCode,
                               java.util.function.Consumer<ExecutionGateParamsYaml> paramsFiller) {
        final ExecutionGate gate = new ExecutionGate();
        gate.scope = scope.name();
        gate.refKey = refKey;
        gate.blockedUntil = blockedUntil;
        gate.createdOn = System.currentTimeMillis();
        gate.reasonCode = reasonCode;

        final ExecutionGateParamsYaml egpy = new ExecutionGateParamsYaml();
        paramsFiller.accept(egpy);
        gate.updateParams(egpy);

        final ExecutionGate saved = executionGateRepository.save(gate);
        assertNotNull(saved.id);
        created.add(saved.id);
        return saved;
    }
}

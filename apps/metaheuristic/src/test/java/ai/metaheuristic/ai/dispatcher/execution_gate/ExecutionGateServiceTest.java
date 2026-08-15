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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The database-first write path: a block becomes visible in memory only once its row is committed.
 *
 * <p>The rollback test is the one that earns its keep. Writing memory first would look identical in
 * every passing scenario and only diverge when a transaction fails — leaving a block in force that no
 * row justifies, which nothing would ever clear because the expiry sweep works from the table.
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
public class ExecutionGateServiceTest extends MhSharedItTest {

    @Autowired private ExecutionGateService executionGateService;
    @Autowired private ExecutionGateRepository executionGateRepository;

    private final List<String> blockedRefKeys = new ArrayList<>();

    @AfterEach
    public void releaseWhatThisTestBlocked() {
        // the context and the DB are shared for the whole run, so a block left in force here would
        // withhold work from every class that follows
        for (String refKey : blockedRefKeys) {
            executionGateService.release(Enums.GateScope.api, refKey);
            final ExecutionGate leftover = executionGateRepository.findByScopeAndRefKey(Enums.GateScope.api.name(), refKey);
            if (leftover != null) {
                executionGateRepository.delete(leftover);
            }
        }
        blockedRefKeys.clear();
    }

    @Test
    public void test_committedQuarantineBecomesVisibleAfterTheAfterCommitHop() {
        final String refKey = SharedItEnv.uniqueCode("gate-svc-commit");
        blockedRefKeys.add(refKey);

        assertNull(executionGateService.blockedUntil(Enums.GateScope.api, refKey),
                "nothing may be blocked before the test blocks it");

        final long blockedUntil = System.currentTimeMillis() + 600_000L;
        executionGateService.quarantine(Enums.GateScope.api, refKey, blockedUntil, "downtime", params());

        // the row is committed synchronously; only the memory update takes the async hop
        assertNotNull(executionGateRepository.findByScopeAndRefKey(Enums.GateScope.api.name(), refKey),
                "the row must be committed before memory is asked about it");

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
                .until(() -> executionGateService.blockedUntil(Enums.GateScope.api, refKey) != null);

        assertEquals(blockedUntil, executionGateService.blockedUntil(Enums.GateScope.api, refKey));
    }

    @Test
    public void test_quarantineIsIdempotent_aSecondCallExtendsTheSameRow() {
        final String refKey = SharedItEnv.uniqueCode("gate-svc-extend");
        blockedRefKeys.add(refKey);

        final long first = System.currentTimeMillis() + 60_000L;
        executionGateService.quarantine(Enums.GateScope.api, refKey, first, "downtime", params());
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
                .until(() -> executionGateService.blockedUntil(Enums.GateScope.api, refKey) != null);

        final long second = System.currentTimeMillis() + 900_000L;
        executionGateService.quarantine(Enums.GateScope.api, refKey, second, "downtime", params());
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
                .until(() -> {
                    final Long until = executionGateService.blockedUntil(Enums.GateScope.api, refKey);
                    return until != null && until == second;
                });

        // one row, not two - the unique index would have refused a second, but the point is that the
        // service never tries to create one
        assertEquals(1, countRows(refKey));
    }

    @Test
    public void test_aShorterSecondQuarantineDoesNotShortenTheBlock() {
        final String refKey = SharedItEnv.uniqueCode("gate-svc-noshorten");
        blockedRefKeys.add(refKey);

        final long longDeadline = System.currentTimeMillis() + 900_000L;
        executionGateService.quarantine(Enums.GateScope.api, refKey, longDeadline, "manual", params());
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
                .until(() -> executionGateService.blockedUntil(Enums.GateScope.api, refKey) != null);

        executionGateService.quarantine(Enums.GateScope.api, refKey, System.currentTimeMillis() + 1_000L, "downtime", params());

        assertEquals(longDeadline, executionGateService.blockedUntil(Enums.GateScope.api, refKey));
    }

    @Test
    public void test_releaseClearsBothTheRowAndMemory() {
        final String refKey = SharedItEnv.uniqueCode("gate-svc-release");
        blockedRefKeys.add(refKey);

        executionGateService.quarantine(Enums.GateScope.api, refKey, System.currentTimeMillis() + 600_000L, "downtime", params());
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
                .until(() -> executionGateService.blockedUntil(Enums.GateScope.api, refKey) != null);

        executionGateService.release(Enums.GateScope.api, refKey);

        assertNull(executionGateRepository.findByScopeAndRefKey(Enums.GateScope.api.name(), refKey));
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
                .until(() -> executionGateService.blockedUntil(Enums.GateScope.api, refKey) == null);
    }

    @Test
    public void test_aRolledBackTransactionLeavesMemoryUntouched() {
        // REF_KEY is VARCHAR(500); an over-long value makes the insert fail, which is a real rollback
        // rather than a simulated one - no test-only hook in the production path
        final String refKey = "x".repeat(600);

        assertNull(executionGateService.blockedUntil(Enums.GateScope.api, refKey));

        assertThrows(Exception.class,
                () -> executionGateService.quarantine(Enums.GateScope.api, refKey, System.currentTimeMillis() + 600_000L, "downtime", params()),
                "the insert must fail, otherwise this test proves nothing");

        assertNull(executionGateService.blockedUntil(Enums.GateScope.api, refKey),
                "a transaction that never committed must leave no block behind");
        assertNull(executionGateRepository.findByScopeAndRefKey(Enums.GateScope.api.name(), refKey));
    }

    @Test
    public void test_quarantineWithAnAlreadyPassedDeadlineIsRefused() {
        final String refKey = SharedItEnv.uniqueCode("gate-svc-expired");
        blockedRefKeys.add(refKey);

        executionGateService.quarantine(Enums.GateScope.api, refKey, System.currentTimeMillis() - 1_000L, "downtime", params());

        assertNull(executionGateService.blockedUntil(Enums.GateScope.api, refKey));
        assertNull(executionGateRepository.findByScopeAndRefKey(Enums.GateScope.api.name(), refKey),
                "an already-expired request must not create a row that the sweep then has to remove");
    }

    private long countRows(String refKey) {
        return executionGateRepository.findByScopeAndRefKey(Enums.GateScope.api.name(), refKey) == null ? 0 : 1;
    }

    private static ExecutionGateParamsYaml params() {
        final ExecutionGateParamsYaml egpy = new ExecutionGateParamsYaml();
        egpy.functionCode = "some-function:1.1";
        egpy.matchedPattern = "(?i)rate.limit";
        return egpy;
    }
}

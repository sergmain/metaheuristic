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
import ai.metaheuristic.ai.dispatcher.data.GateData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * The admission-gate arithmetic, without a Spring context.
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
@Execution(CONCURRENT)
public class ExecutionGateUtilsTest {

    private static final long NOW = 1_000_000L;

    @Test
    public void test_isLive_beforeTheDeadline() {
        assertTrue(ExecutionGateUtils.isLive(NOW + 1, NOW));
        assertTrue(ExecutionGateUtils.isLive(NOW + 60_000L, NOW));
    }

    @Test
    public void test_isLive_atAndAfterTheDeadline() {
        // exclusive boundary: at exactly the deadline the block is over, so a zero-length block is a
        // no-op rather than something that lingers for a millisecond
        assertFalse(ExecutionGateUtils.isLive(NOW, NOW));
        assertFalse(ExecutionGateUtils.isLive(NOW - 1, NOW));
    }

    @Test
    public void test_resolveDeadline_withNothingInForce() {
        assertEquals(NOW + 5_000L, ExecutionGateUtils.resolveDeadline(null, NOW + 5_000L, NOW));
    }

    @Test
    public void test_resolveDeadline_aLongerRequestWins() {
        final GateData.GateRecord existing = new GateData.GateRecord(EnumsApi.GateScope.api, "k", NOW + 1_000L, "downtime");
        assertEquals(NOW + 9_000L, ExecutionGateUtils.resolveDeadline(existing, NOW + 9_000L, NOW));
    }

    @Test
    public void test_resolveDeadline_aShorterRequestCannotShortenALiveBlock() {
        // the case that matters: an analyzer asking for 30s must not release a key someone blocked
        // for an hour
        final GateData.GateRecord existing = new GateData.GateRecord(EnumsApi.GateScope.api, "k", NOW + 3_600_000L, "manual");
        assertEquals(NOW + 3_600_000L, ExecutionGateUtils.resolveDeadline(existing, NOW + 30_000L, NOW));
    }

    @Test
    public void test_resolveDeadline_anExpiredRecordDoesNotExtendAnything() {
        final GateData.GateRecord expired = new GateData.GateRecord(EnumsApi.GateScope.api, "k", NOW - 1L, "downtime");
        assertEquals(NOW + 10L, ExecutionGateUtils.resolveDeadline(expired, NOW + 10L, NOW));
    }

    @Test
    public void test_blockedUntil_reportsALiveBlockAndIgnoresAnExpiredOne() {
        final Map<GateData.GateKey, GateData.GateRecord> records = new HashMap<>();
        ExecutionGateUtils.putOrExtend(records, EnumsApi.GateScope.function, "live", NOW + 5_000L, "downtime", NOW);
        ExecutionGateUtils.putOrExtend(records, EnumsApi.GateScope.function, "gone", NOW + 1L, "downtime", NOW);

        assertEquals(NOW + 5_000L, ExecutionGateUtils.blockedUntil(records, EnumsApi.GateScope.function, "live", NOW));
        // at NOW + 5000 the second one has long expired
        assertNull(ExecutionGateUtils.blockedUntil(records, EnumsApi.GateScope.function, "gone", NOW + 5_000L));
    }

    @Test
    public void test_blockedUntil_isNullForAnUnknownKey() {
        assertNull(ExecutionGateUtils.blockedUntil(new HashMap<>(), EnumsApi.GateScope.processor, "never-blocked", NOW));
    }

    @Test
    public void test_blockedUntil_isScopedNotJustKeyed() {
        // one Function code and one processor id can be the same string without being the same subject
        final Map<GateData.GateKey, GateData.GateRecord> records = new HashMap<>();
        ExecutionGateUtils.putOrExtend(records, EnumsApi.GateScope.function, "17", NOW + 5_000L, "downtime", NOW);

        assertNotNull(ExecutionGateUtils.blockedUntil(records, EnumsApi.GateScope.function, "17", NOW));
        assertNull(ExecutionGateUtils.blockedUntil(records, EnumsApi.GateScope.processor, "17", NOW));
    }

    @Test
    public void test_putOrExtend_isIdempotent_secondCallExtendsAndDoesNotStack() {
        final Map<GateData.GateKey, GateData.GateRecord> records = new HashMap<>();

        ExecutionGateUtils.putOrExtend(records, EnumsApi.GateScope.api, "key", NOW + 1_000L, "downtime", NOW);
        assertEquals(1, records.size());

        final GateData.GateRecord second =
                ExecutionGateUtils.putOrExtend(records, EnumsApi.GateScope.api, "key", NOW + 8_000L, "downtime", NOW);

        assertEquals(1, records.size(), "re-blocking a key must extend the one record, never add a second");
        assertEquals(NOW + 8_000L, second.blockedUntil());
        assertEquals(NOW + 8_000L, ExecutionGateUtils.blockedUntil(records, EnumsApi.GateScope.api, "key", NOW));
    }

    @Test
    public void test_putOrExtend_aShorterSecondCallLeavesTheLongerDeadlineInPlace() {
        final Map<GateData.GateKey, GateData.GateRecord> records = new HashMap<>();

        ExecutionGateUtils.putOrExtend(records, EnumsApi.GateScope.api, "key", NOW + 8_000L, "manual", NOW);
        ExecutionGateUtils.putOrExtend(records, EnumsApi.GateScope.api, "key", NOW + 1_000L, "downtime", NOW);

        assertEquals(1, records.size());
        assertEquals(NOW + 8_000L, ExecutionGateUtils.blockedUntil(records, EnumsApi.GateScope.api, "key", NOW));
    }

    @Test
    public void test_dropExpired_removesOnlyWhatHasPassed() {
        final Map<GateData.GateKey, GateData.GateRecord> records = new HashMap<>();
        ExecutionGateUtils.putOrExtend(records, EnumsApi.GateScope.api, "a", NOW + 10_000L, "downtime", NOW);
        ExecutionGateUtils.putOrExtend(records, EnumsApi.GateScope.api, "b", NOW + 20_000L, "downtime", NOW);
        ExecutionGateUtils.putOrExtend(records, EnumsApi.GateScope.api, "c", NOW + 30_000L, "downtime", NOW);

        final int dropped = ExecutionGateUtils.dropExpired(records, NOW + 25_000L);

        assertEquals(2, dropped);
        assertEquals(1, records.size());
        assertNotNull(ExecutionGateUtils.blockedUntil(records, EnumsApi.GateScope.api, "c", NOW + 25_000L));
    }

    @Test
    public void test_readinessEntryExpired_afterTwoHours() {
        final long twoHours = ExecutionGateUtils.READINESS_TTL_MILLIS;

        assertFalse(ExecutionGateUtils.readinessEntryExpired(NOW, NOW), "just touched");
        assertFalse(ExecutionGateUtils.readinessEntryExpired(NOW, NOW + twoHours), "exactly at the limit is not yet stale");
        assertTrue(ExecutionGateUtils.readinessEntryExpired(NOW, NOW + twoHours + 1), "one millisecond past the limit is stale");
    }

    @Test
    public void test_dropExpired_onAnEmptyMapIsANoOp() {
        final Map<GateData.GateKey, GateData.GateRecord> records = new HashMap<>();
        assertEquals(0, ExecutionGateUtils.dropExpired(records, NOW));
        assertTrue(records.isEmpty());
    }
}

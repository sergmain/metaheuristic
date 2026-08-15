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

package ai.metaheuristic.ai.dispatcher.monitoring;

import ai.metaheuristic.ai.Enums;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
@Execution(CONCURRENT)
public class GateMonitoringTest {

    private static final long T0 = 1_700_000_000_000L;
    private static final long MINUTE = RejectionCounters.BUCKET_MILLIS;

    // ---- window ------------------------------------------------------------------------------

    @Test
    public void test_windowSum_dropsABucketOlderThanTheWindow() {
        final RejectionCounters counters = new RejectionCounters(3, 10);

        counters.increment("fn-a", T0);
        counters.increment("fn-a", T0 + MINUTE);
        counters.increment("fn-a", T0 + 2 * MINUTE);
        assertEquals(3, counters.windowSum("fn-a", T0 + 2 * MINUTE));

        // one minute later the first bucket has fallen out of a 3-bucket window
        counters.increment("fn-a", T0 + 3 * MINUTE);
        assertEquals(3, counters.windowSum("fn-a", T0 + 3 * MINUTE),
                "the oldest bucket must leave the window as a new one enters");
    }

    @Test
    public void test_windowSum_isZeroOnceEverythingHasAgedOut() {
        final RejectionCounters counters = new RejectionCounters(3, 10);
        counters.increment("fn-a", T0);

        assertEquals(0, counters.windowSum("fn-a", T0 + 10 * MINUTE),
                "a level from ten minutes ago is not a level now - that is the whole point of bucketing");
    }

    @Test
    public void test_bucketsPresent_countsHowMuchOfTheWindowAReasonSpans() {
        final RejectionCounters counters = new RejectionCounters(5, 10);
        counters.increment("fn-a", T0);
        counters.increment("fn-a", T0 + MINUTE);
        counters.increment("fn-a", T0 + 2 * MINUTE);

        assertEquals(3, counters.bucketsPresent("fn-a", T0 + 2 * MINUTE));

        // a burst inside one minute spans one bucket however big it is
        final RejectionCounters burst = new RejectionCounters(5, 10);
        for (int i = 0; i < 500; i++) {
            burst.increment("fn-b", T0 + 1_000L);
        }
        assertEquals(500, burst.windowSum("fn-b", T0));
        assertEquals(1, burst.bucketsPresent("fn-b", T0),
                "persistence and volume are different things, and only the first means broken");
    }

    // ---- key cap -----------------------------------------------------------------------------

    @Test
    public void test_theKeySetStopsAllocatingAtTheCapAndCountsTheRestAsOther() {
        // the key includes a function code, which is accident- and attacker-controlled
        final RejectionCounters counters = new RejectionCounters(3, 4);

        for (int i = 0; i < 50; i++) {
            counters.increment("fn-" + i, T0);
        }

        final Map<String, Long> totals = counters.windowTotals(T0);
        assertEquals(5, totals.size(), "4 real keys plus 'other', and no more however many arrive");
        assertEquals(46, totals.get(RejectionCounters.OTHER_KEY),
                "the overflow is still counted - the level stays truthful even when the breakdown stops being complete");

        long sum = totals.values().stream().mapToLong(Long::longValue).sum();
        assertEquals(50, sum, "no rejection may be lost by the cap");
    }

    @Test
    public void test_aKeyAlreadyPresentKeepsCountingAfterTheCapIsReached() {
        final RejectionCounters counters = new RejectionCounters(3, 2);
        counters.increment("fn-a", T0);
        counters.increment("fn-b", T0);
        counters.increment("fn-c", T0);
        counters.increment("fn-a", T0);

        final Map<String, Long> totals = counters.windowTotals(T0);
        assertEquals(2, totals.get("fn-a"), "the cap must not stop an established key from counting");
        assertEquals(1, totals.get(RejectionCounters.OTHER_KEY));
    }

    // ---- exemplar ring -----------------------------------------------------------------------

    @Test
    public void test_theExemplarRingOverwritesInPlaceAndNeverGrows() {
        final ExemplarRing ring = new ExemplarRing(3);

        for (int i = 0; i < 100; i++) {
            ring.add(T0 + i, (long) i, "fn", 7L, "value-" + i);
        }

        assertEquals(3, ring.size());
        assertEquals(3, ring.capacity());

        final List<ExemplarRing.Exemplar> newest = ring.newestFirst();
        assertEquals(3, newest.size());
        assertEquals(99L, newest.get(0).taskId(), "newest first, so a reader sees what is happening now");
        assertEquals(98L, newest.get(1).taskId());
        assertEquals(97L, newest.get(2).taskId());
    }

    @Test
    public void test_anOffendingValueIsTruncatedNotStoredWhole() {
        // console output and env strings are unbounded; this buffer lives for the process's lifetime
        final ExemplarRing ring = new ExemplarRing(1);
        ring.add(T0, 1L, "fn", 7L, "x".repeat(5_000));

        final String stored = ring.newestFirst().get(0).offendingValue();
        assertNotNull(stored);
        assertTrue(stored.length() <= ExemplarRing.MAX_VALUE_LEN + 3);
    }

    @Test
    public void test_theExemplarRingToleratesEmptyAndRejectsAZeroCapacity() {
        assertTrue(new ExemplarRing(2).newestFirst().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new ExemplarRing(0));
    }

    // ---- classification and the admin view ---------------------------------------------------

    @Test
    public void test_classification_matchesTheThreeClasses() {
        assertEquals(GateMonitoring.RejectionClass.benign,
                GateMonitoring.classify(Enums.TaskRejectingStatus.task_in_progress_already));
        assertEquals(GateMonitoring.RejectionClass.transient_,
                GateMonitoring.classify(Enums.TaskRejectingStatus.functions_not_ready));
        assertEquals(GateMonitoring.RejectionClass.transient_,
                GateMonitoring.classify(Enums.TaskRejectingStatus.api_is_quarantined));
        assertEquals(GateMonitoring.RejectionClass.actionable,
                GateMonitoring.classify(Enums.TaskRejectingStatus.interpreter_is_undefined));
        assertEquals(GateMonitoring.RejectionClass.actionable,
                GateMonitoring.classify(Enums.TaskRejectingStatus.tags_arent_allowed));
    }

    @Test
    public void test_aBenignRejectionIsCountedButExcludedFromTheActionableView() {
        final GateMonitoring monitoring = new GateMonitoring();
        monitoring.recordRejection(Enums.TaskRejectingStatus.task_in_progress_already, "fn-a", 1L, 7L, null, T0);

        assertEquals(1, monitoring.countOf(Enums.TaskRejectingStatus.task_in_progress_already, T0),
                "counted, so totals stay honest");
        assertTrue(monitoring.actionableView(T0).isEmpty(),
                "not shown - most rejections are normal, and showing them buries the ones that are not");
    }

    @Test
    public void test_theActionableViewRanksActionableAboveTransient() {
        final GateMonitoring monitoring = new GateMonitoring();
        // the transient one is far louder, and must still rank below
        for (int i = 0; i < 100; i++) {
            monitoring.recordRejection(Enums.TaskRejectingStatus.functions_not_ready, "fn-a", 1L, 7L, null, T0);
        }
        monitoring.recordRejection(Enums.TaskRejectingStatus.interpreter_is_undefined, "fn-b", 2L, 7L, "python-4", T0);

        final List<GateMonitoring.ReasonLevel> view = monitoring.actionableView(T0);

        assertEquals(2, view.size());
        assertEquals(Enums.TaskRejectingStatus.interpreter_is_undefined, view.get(0).reason(),
                "a human must change something - that outranks a loud self-resolving reason");
        assertEquals(Enums.TaskRejectingStatus.functions_not_ready, view.get(1).reason());
        assertEquals(100, view.get(1).count());
    }

    @Test
    public void test_withinAClassPersistenceOutranksVolume() {
        final GateMonitoring monitoring = new GateMonitoring();
        // a spike in one minute
        for (int i = 0; i < 500; i++) {
            monitoring.recordRejection(Enums.TaskRejectingStatus.git_required, "fn-spike", 1L, 7L, null, T0);
        }
        // a trickle across five
        for (int m = 0; m < 5; m++) {
            monitoring.recordRejection(Enums.TaskRejectingStatus.tags_arent_allowed, "fn-steady", 2L, 7L, null, T0 + m * MINUTE);
        }

        final List<GateMonitoring.ReasonLevel> view = monitoring.actionableView(T0 + 4 * MINUTE);

        assertEquals(Enums.TaskRejectingStatus.tags_arent_allowed, view.get(0).reason(),
                "five minutes of a thing is broken; one minute of a lot of it is usually a deploy");
    }

    @Test
    public void test_theActionableViewCarriesTheExemplarThatEndsTheInvestigation() {
        // this is the 2026-07-24 case: the count says a Processor is refusing work, the exemplar says why
        final GateMonitoring monitoring = new GateMonitoring();
        monitoring.recordRejection(Enums.TaskRejectingStatus.interpreter_is_undefined,
                "fn-b", 42L, 7L, "python-4-that-nobody-declares", T0);

        final GateMonitoring.ReasonLevel level = monitoring.actionableView(T0).get(0);

        assertEquals(1, level.exemplars().size());
        assertEquals("python-4-that-nobody-declares", level.exemplars().get(0).offendingValue());
        assertEquals(42L, level.exemplars().get(0).taskId());
    }

    @Test
    public void test_aReasonThatHasAgedOutOfTheWindowLeavesTheView() {
        final GateMonitoring monitoring = new GateMonitoring();
        monitoring.recordRejection(Enums.TaskRejectingStatus.git_required, "fn-a", 1L, 7L, null, T0);

        assertEquals(1, monitoring.actionableView(T0).size());
        assertTrue(monitoring.actionableView(T0 + 60 * MINUTE).isEmpty(),
                "the view answers 'what is wrong now', not 'what was ever wrong'");
    }
}

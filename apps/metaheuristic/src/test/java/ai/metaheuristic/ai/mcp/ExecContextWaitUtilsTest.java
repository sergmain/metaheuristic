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

package ai.metaheuristic.ai.mcp;

import ai.metaheuristic.ai.mcp.ExecContextWaitUtils.Probe;
import ai.metaheuristic.ai.mcp.ExecContextWaitUtils.Until;
import ai.metaheuristic.ai.mcp.ExecContextWaitUtils.WaitResult;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.EnumsApi.ExecContextState;
import ai.metaheuristic.api.EnumsApi.TaskExecState;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The wait loop of {@code mh_wait_exec_context}, run against an in-memory ExecContext whose state moves as time passes.
 *
 * <p>{@link World} is not a scripted double: it is one small implementation of "an ExecContext over time" - the
 * clock advances only through its sleeper and the changes of a test's timeline land when the clock passes them, the
 * same way for every test. Production passes the real probe, segment read, clock and sleeper into the same parameters.
 * Every assertion is on the {@link WaitResult} the loop returned.
 *
 * @author Serge
 * Date: 10/10/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class ExecContextWaitUtilsTest {

    private static final long POLL = 1000;

    /** An ExecContext over time. Every segment write raises {@code writes}, which stands in for changeVersion. */
    private static final class World {
        long now = 0;
        boolean exists = true;
        ExecContextState state = ExecContextState.STARTED;
        long writes = 0;
        final Map<Long, TaskExecState> tasks = new HashMap<>();
        final NavigableMap<Long, List<Consumer<World>>> timeline = new TreeMap<>();

        World task(long taskId, TaskExecState s) {
            tasks.put(taskId, s);
            return this;
        }

        World at(long millis, Consumer<World> change) {
            timeline.computeIfAbsent(millis, k -> new ArrayList<>()).add(change);
            return this;
        }

        void write(long taskId, TaskExecState s) {
            tasks.put(taskId, s);
            writes++;
        }

        @Nullable
        Probe probe() {
            return exists ? new Probe(state, "w" + writes) : null;
        }

        Map<Long, TaskExecState> taskStates() {
            return Map.copyOf(tasks);
        }

        void sleep(long millis) {
            now += millis;
            final NavigableMap<Long, List<Consumer<World>>> due = timeline.headMap(now, true);
            due.values().forEach(changes -> changes.forEach(c -> c.accept(this)));
            due.clear();
        }

        WaitResult await(Until until, @Nullable String sinceVersion, long maxMillis) {
            return ExecContextWaitUtils.await(until, sinceVersion, maxMillis, POLL,
                    this::probe, this::taskStates, () -> now, this::sleep);
        }
    }

    // ==================== isTerminal ====================

    @Test
    public void test_isTerminal_theFourStatesPollingStopsOn() {
        for (ExecContextState s : List.of(ExecContextState.FINISHED, ExecContextState.ERROR,
                ExecContextState.STOPPED, ExecContextState.DOESNT_EXIST)) {
            assertTrue(ExecContextWaitUtils.isTerminal(s), s.name());
        }
    }

    @Test
    public void test_isTerminal_runningStatesAreNot() {
        for (ExecContextState s : List.of(ExecContextState.NONE, ExecContextState.STARTED,
                ExecContextState.CLONING, ExecContextState.UNKNOWN)) {
            assertFalse(ExecContextWaitUtils.isTerminal(s), s.name());
        }
    }

    // ==================== TERMINAL ====================

    @Test
    public void test_terminal_alreadyFinished_returnsAtOnce() {
        final World w = new World();
        w.state = ExecContextState.FINISHED;

        final WaitResult r = w.await(Until.TERMINAL, null, 25_000);

        assertTrue(r.met());
        assertTrue(r.terminal());
        assertFalse(r.timedOut());
        assertEquals(0, r.waitedMillis());
        assertEquals("FINISHED", r.stateName());
    }

    @Test
    public void test_terminal_waitsUntilTheExecContextFinishes() {
        final World w = new World().at(3000, x -> x.state = ExecContextState.FINISHED);

        final WaitResult r = w.await(Until.TERMINAL, null, 25_000);

        assertTrue(r.met());
        assertTrue(r.terminal());
        assertEquals(3000, r.waitedMillis());
        assertEquals("FINISHED", r.stateName());
    }

    @Test
    public void test_terminal_nothingChanges_timesOutAtMaxMillis() {
        final World w = new World();

        final WaitResult r = w.await(Until.TERMINAL, null, 5000);

        assertFalse(r.met());
        assertFalse(r.terminal());
        assertTrue(r.timedOut());
        assertEquals(5000, r.waitedMillis());
        assertEquals("STARTED", r.stateName());
    }

    @Test
    public void test_timeout_lastSleepIsCutToTheWindow() {
        final World w = new World();

        final WaitResult r = w.await(Until.TERMINAL, null, 2500);

        assertTrue(r.timedOut());
        assertEquals(2500, r.waitedMillis(), "the last sleep must stop at maxMillis, not overrun it by a whole poll");
    }

    // ==================== ANY_ERROR ====================

    @Test
    public void test_anyError_metWhenATaskGoesToError() {
        final World w = new World()
                .task(1, TaskExecState.IN_PROGRESS).task(2, TaskExecState.IN_PROGRESS).task(3, TaskExecState.NONE)
                .at(2000, x -> x.write(2, TaskExecState.ERROR));

        final WaitResult r = w.await(Until.ANY_ERROR, null, 25_000);

        assertTrue(r.met());
        assertFalse(r.terminal());
        assertEquals(2000, r.waitedMillis());
        assertEquals(List.of(2L), r.firstErrorTaskIds());
        assertEquals(Map.of("ERROR", 1, "IN_PROGRESS", 1, "NONE", 1), r.taskStateCounts());
    }

    @Test
    public void test_anyError_errorWithRecoveryIsNotAnError() {
        final World w = new World()
                .task(1, TaskExecState.IN_PROGRESS)
                .at(1000, x -> x.write(1, TaskExecState.ERROR_WITH_RECOVERY));

        final WaitResult r = w.await(Until.ANY_ERROR, null, 4000);

        assertFalse(r.met(), "ERROR_WITH_RECOVERY is re-run, not failed");
        assertTrue(r.timedOut());
        assertEquals(List.of(), r.firstErrorTaskIds());
        assertEquals(Map.of("ERROR_WITH_RECOVERY", 1), r.taskStateCounts());
    }

    @Test
    public void test_anyError_endsOnTerminalEvenWithoutAnError() {
        final World w = new World()
                .task(1, TaskExecState.IN_PROGRESS)
                .at(2000, x -> {
                    x.write(1, TaskExecState.OK);
                    x.state = ExecContextState.FINISHED;
                });

        final WaitResult r = w.await(Until.ANY_ERROR, null, 25_000);

        assertFalse(r.met());
        assertTrue(r.terminal(), "a finished ExecContext can't produce the error waited for, so the wait must end");
        assertFalse(r.timedOut());
        assertEquals(2000, r.waitedMillis());
        assertEquals(Map.of("OK", 1), r.taskStateCounts());
    }

    // ==================== STATE_CHANGED ====================

    @Test
    public void test_stateChanged_withoutSinceVersion_returnsTheCurrentVersionAtOnce() {
        final World w = new World().task(1, TaskExecState.NONE);

        final WaitResult r = w.await(Until.STATE_CHANGED, null, 25_000);

        assertTrue(r.met());
        assertEquals(0, r.waitedMillis());
        assertEquals(ExecContextState.STARTED.code + ":w0", r.version());
        assertEquals(Map.of("NONE", 1), r.taskStateCounts());
    }

    @Test
    public void test_stateChanged_sameVersion_waitsForTheNextWrite() {
        final World w = new World()
                .task(1, TaskExecState.NONE)
                .at(3000, x -> x.write(1, TaskExecState.IN_PROGRESS));
        final String since = w.await(Until.STATE_CHANGED, null, 25_000).version();

        final WaitResult r = w.await(Until.STATE_CHANGED, since, 25_000);

        assertTrue(r.met());
        assertEquals(3000, r.waitedMillis());
        assertNotEquals(since, r.version());
        assertEquals(Map.of("IN_PROGRESS", 1), r.taskStateCounts());
    }

    @Test
    public void test_stateChanged_execContextStateAloneIsAChange() {
        // NONE -> STARTED writes no segment, so changeVersion alone would miss it; the version carries the state too
        final World w = new World().at(1000, x -> x.state = ExecContextState.STARTED);
        w.state = ExecContextState.NONE;
        final String since = w.await(Until.STATE_CHANGED, null, 25_000).version();

        final WaitResult r = w.await(Until.STATE_CHANGED, since, 25_000);

        assertTrue(r.met());
        assertEquals("STARTED", r.stateName());
        assertEquals(1000, r.waitedMillis());
    }

    @Test
    public void test_stateChanged_writeBetweenProbeAndStates_isReportedAgainNotLost() {
        // a segment write landing after the probe and before the Task states are read: the version handed back is
        // the probe's, so the caller's next STATE_CHANGED sees the newer version at once instead of waiting past it
        final World w = new World().task(1, TaskExecState.NONE);
        final WaitResult first = ExecContextWaitUtils.await(Until.STATE_CHANGED, null, 25_000, POLL,
                w::probe,
                () -> {
                    final Map<Long, TaskExecState> states = w.taskStates();
                    w.write(1, TaskExecState.IN_PROGRESS);
                    return states;
                },
                () -> w.now, w::sleep);

        final WaitResult second = w.await(Until.STATE_CHANGED, first.version(), 25_000);

        assertTrue(second.met());
        assertEquals(0, second.waitedMillis());
        assertEquals(Map.of("IN_PROGRESS", 1), second.taskStateCounts());
    }

    // ==================== the ExecContext disappears ====================

    @Test
    public void test_gone_endsTheWaitAsDoesntExist() {
        final World w = new World().at(2000, x -> x.exists = false);

        final WaitResult r = w.await(Until.ANY_ERROR, null, 25_000);

        assertFalse(r.met());
        assertTrue(r.terminal());
        assertEquals("DOESNT_EXIST", r.stateName());
        assertEquals(2000, r.waitedMillis());
    }

    // ==================== the summary ====================

    @Test
    public void test_summary_errorTaskIdsAreSortedAndCapped() {
        final World w = new World();
        for (long id : List.of(70L, 60L, 50L, 40L, 30L, 20L, 10L)) {
            w.task(id, TaskExecState.ERROR);
        }

        final WaitResult r = w.await(Until.ANY_ERROR, null, 25_000);

        assertEquals(List.of(10L, 20L, 30L, 40L, 50L), r.firstErrorTaskIds());
        assertEquals(ExecContextWaitUtils.MAX_ERROR_TASK_IDS, r.firstErrorTaskIds().size());
        assertEquals(Map.of("ERROR", 7), r.taskStateCounts());
    }

    @Test
    public void test_summary_countsAreNonZeroOnlyAndOrderedByName() {
        final World w = new World()
                .task(1, TaskExecState.OK).task(2, TaskExecState.OK).task(3, TaskExecState.NONE).task(4, TaskExecState.IN_PROGRESS);

        final WaitResult r = w.await(Until.STATE_CHANGED, null, 25_000);

        assertEquals(List.of("IN_PROGRESS", "NONE", "OK"), List.copyOf(r.taskStateCounts().keySet()));
        assertEquals(Map.of("IN_PROGRESS", 1, "NONE", 1, "OK", 2), r.taskStateCounts());
    }

    @Test
    public void test_isMet_stateChangedComparesTheWholeVersion() {
        final Probe p = new Probe(ExecContextState.STARTED, "4,10,1,2");

        assertFalse(ExecContextWaitUtils.isMet(Until.STATE_CHANGED, p, p.version(), Map.of()));
        assertTrue(ExecContextWaitUtils.isMet(Until.STATE_CHANGED, p, EnumsApi.ExecContextState.NONE.code + ":4,10,1,2", Map.of()));
        assertTrue(ExecContextWaitUtils.isMet(Until.STATE_CHANGED, p, ExecContextState.STARTED.code + ":4,11,1,2", Map.of()));
    }
}

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

package ai.metaheuristic.ai.dispatcher.exec_context_segment;

import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4 of 041-EXEC-CONTEXT-SEGMENTS-PLAN (section 8.5): the SKIPPED closure of a failure, walked along the lines from
 * the failed Task, equals what today's whole-graph propagation produced (the goldens), and has the plan's properties.
 *
 * <p>Phase 9: the same goldens through {@link SegmentStateChange} - the closure as segmented storage runs it, reading a
 * join's dead parents from its record's counts instead of walking the lines into it.
 */
@Execution(ExecutionMode.CONCURRENT)
public class SegmentSkipPropagationTest {

    private static final EnumsApi.TaskExecState OK = EnumsApi.TaskExecState.OK;
    private static final EnumsApi.TaskExecState ERROR = EnumsApi.TaskExecState.ERROR;
    private static final EnumsApi.TaskExecState SKIPPED = EnumsApi.TaskExecState.SKIPPED;

    private static void assertGolden(SegmentFixtureShapes.DotShape shape) {
        final SegmentStates.Adjacency adj = SegmentStates.Adjacency.of(SegmentAlgebra.decompose(SegmentDotUtils.parse(shape.dot())));
        final SegmentGolden.Golden golden = SegmentGolden.load(shape.id());
        assertFalse(golden.skips().isEmpty(), shape.id() + ": the golden must hold at least one failure");
        for (SegmentGolden.SkipCase c : golden.skips()) {
            final Map<Long, EnumsApi.TaskExecState> states = new TreeMap<>(c.before());
            SegmentStates.skipClosure(adj, states, c.seed());
            assertEquals(new TreeMap<>(c.after()), states,
                    shape.id() + " run " + c.run() + " step " + c.step() + ", failed #" + c.seed() + ": states after the closure differ from the golden");
        }
    }

    @Test public void test_golden_S1() { assertGolden(SegmentFixtureShapes.DOT_S1); }
    @Test public void test_golden_S2() { assertGolden(SegmentFixtureShapes.DOT_S2); }
    @Test public void test_golden_S3() { assertGolden(SegmentFixtureShapes.DOT_S3); }
    @Test public void test_golden_S4() { assertGolden(SegmentFixtureShapes.DOT_S4); }
    @Test public void test_golden_S5() { assertGolden(SegmentFixtureShapes.DOT_S5); }
    @Test public void test_golden_S6() { assertGolden(SegmentFixtureShapes.DOT_S6); }
    @Test public void test_golden_S7() { assertGolden(SegmentFixtureShapes.DOT_S7); }

    /**
     * A storage lookup over every line of a shape, as {@link ExecContextSegmentStateTxService} supplies it from segments:
     * every line registered with its derived join, the counts taken from {@code states}.
     */
    private static SegmentStateChange.Lookup lookup(List<SegmentData.Line> lines, Map<Long, EnumsApi.TaskExecState> states) {
        final SegmentAlgebra.LineIndex index = SegmentAlgebra.LineIndex.of(lines);
        final Map<Long, List<SegmentData.Line>> forked = new HashMap<>();
        final Map<Long, SegmentStateChange.JoinCount> joins = new HashMap<>();
        for (SegmentData.Line l : lines) {
            if (l.isRoot()) {
                continue;
            }
            forked.computeIfAbsent(Objects.requireNonNull(l.forkTaskId()), k -> new ArrayList<>()).add(l);
            final Long join = Objects.requireNonNull(SegmentAlgebra.derivedJoin(index, l));
            final EnumsApi.TaskExecState tail = states.getOrDefault(l.tail().taskId(), EnumsApi.TaskExecState.NONE);
            final SegmentStateChange.JoinCount c = joins.getOrDefault(join, new SegmentStateChange.JoinCount(0, 0, 0));
            joins.put(join, new SegmentStateChange.JoinCount(c.registered() + 1, c.finished() + (tail == OK ? 1 : 0),
                    c.dead() + (tail == ERROR || tail == SKIPPED ? 1 : 0)));
        }
        return new SegmentStateChange.Lookup(index::lineOf, t -> forked.getOrDefault(t, List.of()), joins::get,
                t -> states.getOrDefault(t, EnumsApi.TaskExecState.NONE), ctx -> true);
    }

    private static void assertGoldenOnSegments(SegmentFixtureShapes.DotShape shape) {
        final List<SegmentData.Line> lines = SegmentAlgebra.decompose(SegmentDotUtils.parse(shape.dot()));
        final SegmentGolden.Golden golden = SegmentGolden.load(shape.id());
        assertFalse(golden.skips().isEmpty(), shape.id() + ": the golden must hold at least one failure");
        for (SegmentGolden.SkipCase c : golden.skips()) {
            final String at = shape.id() + " run " + c.run() + " step " + c.step() + ", failed #" + c.seed();
            final Map<Long, EnumsApi.TaskExecState> before = new TreeMap<>(c.before());
            before.remove(c.seed());
            final EnumsApi.TaskExecState failure = Objects.requireNonNull(c.after().get(c.seed()), at + ": no state of the failed Task");

            final SegmentStateChange.Result r = SegmentStateChange.apply(lookup(lines, before),
                    List.of(new SegmentStateChange.Change(c.seed(), failure)));

            final Map<Long, EnumsApi.TaskExecState> after = new TreeMap<>(before);
            after.putAll(r.states());
            assertEquals(new TreeMap<>(c.after()), after, at + ": states after the change differ from the golden");

            final SegmentStateChange.Lookup initial = lookup(lines, before);
            final SegmentStateChange.Lookup recount = lookup(lines, after);
            for (Long join : new TreeSet<>(SegmentAlgebra.derivedJoins(lines).values())) {
                final SegmentStateChange.JoinCount expected = recount.joinOf().apply(join);
                if (Objects.equals(initial.joinOf().apply(join), expected)) {
                    assertFalse(r.joins().containsKey(join), at + ": join #" + join + " did not move, but was written");
                }
                else {
                    assertEquals(expected, r.joins().get(join), at + ": join #" + join + " counts after the change");
                }
            }
        }
    }

    @Test public void test_goldenOnSegments_S1() { assertGoldenOnSegments(SegmentFixtureShapes.DOT_S1); }
    @Test public void test_goldenOnSegments_S2() { assertGoldenOnSegments(SegmentFixtureShapes.DOT_S2); }
    @Test public void test_goldenOnSegments_S3() { assertGoldenOnSegments(SegmentFixtureShapes.DOT_S3); }
    @Test public void test_goldenOnSegments_S4() { assertGoldenOnSegments(SegmentFixtureShapes.DOT_S4); }
    @Test public void test_goldenOnSegments_S5() { assertGoldenOnSegments(SegmentFixtureShapes.DOT_S5); }
    @Test public void test_goldenOnSegments_S6() { assertGoldenOnSegments(SegmentFixtureShapes.DOT_S6); }
    @Test public void test_goldenOnSegments_S7() { assertGoldenOnSegments(SegmentFixtureShapes.DOT_S7); }

    private static SegmentStates.Adjacency adj(SegmentFixtureShapes.DotShape shape) {
        return SegmentStates.Adjacency.of(SegmentAlgebra.decompose(SegmentDotUtils.parse(shape.dot())));
    }

    private static Map<Long, EnumsApi.TaskExecState> ok(long... ids) {
        final Map<Long, EnumsApi.TaskExecState> m = new TreeMap<>();
        for (long id : ids) {
            m.put(id, OK);
        }
        return m;
    }

    @Test
    public void test_failureAtIndexI_killsRestOfItsChainAndWhatItForks() {
        final SegmentStates.Adjacency adj = adj(SegmentFixtureShapes.DOT_S1);
        final Map<Long, EnumsApi.TaskExecState> st = ok(191546, 191547, 191548, 191549, 191550, 191551, 191552, 191553, 191556, 191557, 191558, 191559);
        st.put(191560L, ERROR);
        final Set<Long> marked = SegmentStates.skipClosure(adj, st, 191560L);
        assertTrue(marked.containsAll(List.of(191561L, 191562L)), "S1: the rest of line 1,6#1 after #191560 is killed: " + marked);
        assertTrue(marked.containsAll(List.of(191563L, 191564L, 191565L, 191566L, 191567L, 191568L, 191569L, 191570L, 191571L)),
                "S1: every line forked from the killed Tasks is killed: " + marked);
        assertFalse(marked.contains(191554L), "S1: the terminal join is never marked");
        assertFalse(marked.contains(191555L), "S1: the leaf is never marked");
        assertEquals(11, marked.size(), "S1: exactly the rest of the line and its nested lines: " + marked);
    }

    @Test
    public void test_propagationStopsAtAJoinWithALiveParent() {
        final SegmentStates.Adjacency adj = adj(SegmentFixtureShapes.DOT_S1);
        final Map<Long, EnumsApi.TaskExecState> st = ok(191546, 191547, 191548, 191549, 191550, 191551);
        st.put(191557L, EnumsApi.TaskExecState.IN_PROGRESS);
        st.put(191556L, ERROR);
        assertEquals(Set.of(), SegmentStates.skipClosure(adj, st, 191556L),
                "S1: join #191552 keeps its live parents (fork #191551, line 1,4#1) - nothing is marked");
        assertNull(st.get(191552L), "S1: the join stays NONE");
    }

    @Test
    public void test_terminalAndLeafNeverMarked() {
        final SegmentStates.Adjacency adj = adj(SegmentFixtureShapes.DOT_S4);
        final Map<Long, EnumsApi.TaskExecState> st = new TreeMap<>(Map.of(1L, ERROR));
        assertEquals(Set.of(2L), SegmentStates.skipClosure(adj, st, 1L), "S4: only #2 - #3 is terminal, #4 is the leaf");
        assertNull(st.get(3L));
        assertNull(st.get(4L));
    }

    @Test
    public void test_S7_oneClosureOverAllSeeds_equalsSeedingLineByLine() {
        final SegmentStates.Adjacency adj = adj(SegmentFixtureShapes.DOT_S7);
        final List<Long> heads = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            heads.add(1000 + 2L * i);
        }

        final Map<Long, EnumsApi.TaskExecState> all = ok(1);
        heads.forEach(h -> all.put(h, ERROR));
        heads.forEach(h -> SegmentStates.skipClosure(adj, all, h));

        final Map<Long, EnumsApi.TaskExecState> byLine = ok(1);
        for (Long h : heads) {
            byLine.put(h, ERROR);
            SegmentStates.skipClosure(adj, byLine, h);
        }
        assertEquals(all, byLine, "S7: closing all failures at once equals closing them line by line");
        for (Long h : heads) {
            assertEquals(SKIPPED, all.get(h + 1), "S7: the tail of line with head #" + h + " is SKIPPED");
        }
        assertNull(all.get(2L), "S7: the terminal join is never marked");
    }
}

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
 * Phase 4 of 041-EXEC-CONTEXT-SEGMENTS-PLAN (section 8.5, decision 11): readiness computed from lines - per line only the
 * first unfinished Task, judged by its direct parents - equals what today's whole-graph {@code findAllForAssigning}
 * answered, over 200 seeded reachable states per shape (the goldens).
 */
@Execution(ExecutionMode.CONCURRENT)
public class SegmentReadinessTest {

    private static void assertGolden(SegmentFixtureShapes.DotShape shape) {
        final List<SegmentData.Line> lines = SegmentAlgebra.decompose(SegmentDotUtils.parse(shape.dot()));
        final SegmentStates.Adjacency adj = SegmentStates.Adjacency.of(lines);
        final SegmentGolden.Golden golden = SegmentGolden.load(shape.id());
        assertEquals(200, golden.readiness().size(), shape.id() + ": 200 seeded states expected in the golden");
        for (SegmentGolden.ReadyCase c : golden.readiness()) {
            final Set<Long> ready = SegmentStates.ready(adj, lines, SegmentStates.stateOf(c.states()), true);
            assertEquals(new TreeSet<>(c.ready()), new TreeSet<>(ready),
                    shape.id() + " run " + c.run() + " step " + c.step() + ": ready set differs from the golden, states " + c.states());
        }
    }

    @Test public void test_golden_S1() { assertGolden(SegmentFixtureShapes.DOT_S1); }
    @Test public void test_golden_S2() { assertGolden(SegmentFixtureShapes.DOT_S2); }
    @Test public void test_golden_S3() { assertGolden(SegmentFixtureShapes.DOT_S3); }
    @Test public void test_golden_S4() { assertGolden(SegmentFixtureShapes.DOT_S4); }
    @Test public void test_golden_S5() { assertGolden(SegmentFixtureShapes.DOT_S5); }
    @Test public void test_golden_S6() { assertGolden(SegmentFixtureShapes.DOT_S6); }
    @Test public void test_golden_S7() { assertGolden(SegmentFixtureShapes.DOT_S7); }

    private static final EnumsApi.TaskExecState OK = EnumsApi.TaskExecState.OK;

    private static List<SegmentData.Line> s5() {
        return SegmentAlgebra.decompose(SegmentDotUtils.parse(SegmentFixtureShapes.DOT_S5.dot()));
    }

    @Test
    public void test_lineHeadReadyOnlyWhenForkFinished() {
        final List<SegmentData.Line> lines = s5();
        final SegmentStates.Adjacency adj = SegmentStates.Adjacency.of(lines);
        final Map<Long, EnumsApi.TaskExecState> st = new HashMap<>(Map.of(10L, OK, 20L, EnumsApi.TaskExecState.IN_PROGRESS));
        assertFalse(SegmentStates.ready(adj, lines, SegmentStates.stateOf(st), true).contains(30L),
                "S5: head #30 must wait while its fork #20 is in progress");
        st.put(20L, OK);
        assertEquals(Set.of(30L), SegmentStates.ready(adj, lines, SegmentStates.stateOf(st), true),
                "S5: head #30 is ready once its fork #20 finished");
    }

    @Test
    public void test_joinReadyOnlyWhenEveryLineResolvingToItFinished() {
        final List<SegmentData.Line> lines = s5();
        final SegmentStates.Adjacency adj = SegmentStates.Adjacency.of(lines);
        final Map<Long, EnumsApi.TaskExecState> st = new HashMap<>(Map.of(10L, OK, 20L, OK, 30L, OK));
        assertEquals(Set.of(31L), SegmentStates.ready(adj, lines, SegmentStates.stateOf(st), true),
                "S5: the join #11 waits for the graft's tail #31; only #31 is ready");
        st.put(31L, OK);
        assertEquals(Set.of(11L), SegmentStates.ready(adj, lines, SegmentStates.stateOf(st), true),
                "S5: with every line resolving to #11 finished, the join is ready");
    }

    @Test
    public void test_deadLineAddedToCompletedJoin_leavesItComplete() {
        final List<SegmentData.Line> lines = new ArrayList<>(s5());
        final Map<Long, EnumsApi.TaskExecState> st = new HashMap<>(Map.of(10L, OK, 20L, OK, 30L, OK, 31L, OK));
        assertEquals(Set.of(11L), SegmentStates.ready(SegmentStates.Adjacency.of(lines), lines, SegmentStates.stateOf(st), true));

        // a PLACE_NOW graft: a new line under the same fork #20, laid SKIPPED
        lines.add(new SegmentData.Line("1,3,2|0#2", 20L, List.of(new SegmentData.Vertex(40L, null), new SegmentData.Vertex(41L, null))));
        st.put(40L, EnumsApi.TaskExecState.SKIPPED);
        st.put(41L, EnumsApi.TaskExecState.SKIPPED);
        final SegmentStates.Adjacency adj = SegmentStates.Adjacency.of(lines);
        assertTrue(adj.parentsOf(11L).contains(41L), "the new line resolves to the join #11");
        assertEquals(Set.of(11L), SegmentStates.ready(adj, lines, SegmentStates.stateOf(st), true),
                "a dead line added to a completed join leaves it complete");
    }

    @Test
    public void test_unfinishedRootHeadIsReadyAlone() {
        final List<SegmentData.Line> lines = s5();
        assertEquals(Set.of(10L), SegmentStates.ready(SegmentStates.Adjacency.of(lines), lines, SegmentStates.stateOf(Map.of()), true));
    }
}

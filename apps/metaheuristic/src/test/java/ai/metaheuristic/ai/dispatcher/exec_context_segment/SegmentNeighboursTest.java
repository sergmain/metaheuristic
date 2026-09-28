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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 10 of 041-EXEC-CONTEXT-SEGMENTS-PLAN: a Task's direct children and parents over line lookups - what readers get
 * from segments loaded on demand ({@link SegmentLineView}) - equal the ones implied by every line of the ExecContext
 * ({@link SegmentStates.Adjacency}), for every Task of every shape. The parents of a join include the tails of the lines
 * resolving to it through nested forks (S2, S5, S6: tails wired two levels up).
 */
@Execution(ExecutionMode.CONCURRENT)
public class SegmentNeighboursTest {

    private static void assertNeighbours(SegmentFixtureShapes.DotShape shape) {
        final List<SegmentData.Line> lines = SegmentAlgebra.decompose(SegmentDotUtils.parse(shape.dot()));
        final SegmentStates.Adjacency adj = SegmentStates.Adjacency.of(lines);
        final SegmentAlgebra.LineIndex index = SegmentAlgebra.LineIndex.of(lines);
        final Map<Long, List<SegmentData.Line>> forked = new HashMap<>();
        lines.stream().filter(l -> !l.isRoot()).forEach(l -> forked.computeIfAbsent(Objects.requireNonNull(l.forkTaskId()), k -> new ArrayList<>()).add(l));

        int checked = 0;
        for (SegmentData.Line line : lines) {
            for (SegmentData.Vertex v : line.tasks()) {
                final long id = v.taskId();
                assertEquals(sorted(adj.childrenOf(id)), sorted(SegmentStates.childrenOf(index::lineOf, t -> forked.getOrDefault(t, List.of()), id)),
                        shape.id() + ": children of #" + id);
                assertEquals(sorted(adj.parentsOf(id)), sorted(SegmentStates.parentsOf(index::lineOf, t -> forked.getOrDefault(t, List.of()), id)),
                        shape.id() + ": parents of #" + id);
                checked++;
            }
        }
        assertTrue(checked > 0, shape.id() + ": no Task checked");
    }

    private static List<Long> sorted(List<Long> ids) {
        final List<Long> l = new ArrayList<>(ids);
        Collections.sort(l);
        return l;
    }

    @Test public void test_S1() { assertNeighbours(SegmentFixtureShapes.DOT_S1); }
    @Test public void test_S2() { assertNeighbours(SegmentFixtureShapes.DOT_S2); }
    @Test public void test_S3() { assertNeighbours(SegmentFixtureShapes.DOT_S3); }
    @Test public void test_S4() { assertNeighbours(SegmentFixtureShapes.DOT_S4); }
    @Test public void test_S5() { assertNeighbours(SegmentFixtureShapes.DOT_S5); }
    @Test public void test_S6() { assertNeighbours(SegmentFixtureShapes.DOT_S6); }
    @Test public void test_S7() { assertNeighbours(SegmentFixtureShapes.DOT_S7); }
    @Test public void test_S8() { assertNeighbours(SegmentFixtureShapes.DOT_S8); }
}

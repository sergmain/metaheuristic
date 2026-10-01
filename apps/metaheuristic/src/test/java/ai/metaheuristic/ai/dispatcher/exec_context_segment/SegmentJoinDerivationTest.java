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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4 of 041-EXEC-CONTEXT-SEGMENTS-PLAN (section 8.5): the derived join of a line - the Task after the fork in the
 * fork's chain, or, when the fork is last in its chain, the join of the fork's line, recursively. Expectations are read
 * off the real graphs of ExecContexts 325 and 328; the round trip in {@link SegmentAlgebraTest} proves the derived
 * joins are exactly the tail edges those graphs store.
 */
@Execution(ExecutionMode.CONCURRENT)
public class SegmentJoinDerivationTest {

    private static Map<String, Long> joins(SegmentFixtureShapes.DotShape shape) {
        return SegmentAlgebra.derivedJoins(SegmentAlgebra.decompose(SegmentDotUtils.parse(shape.dot())));
    }

    @Test
    public void test_forkNotLastInChain_joinIsNextTaskOfForkChain() {
        final Map<String, Long> j = joins(SegmentFixtureShapes.DOT_S1);
        assertEquals(191552L, j.get("1,2#0"), "S1: fork #191551 is followed by #191552 in the top chain");
        assertEquals(191552L, j.get("1,4#1"), "S1: the second static branch of the same fork joins the same Task");
        assertEquals(191565L, j.get("1,6,7,8|1|0#0"), "S1: fork #191564 mid-line is followed by #191565");
    }

    @Test
    public void test_forkLastInChain_joinIsEnclosingJoin_recursively() {
        final Map<String, Long> j = joins(SegmentFixtureShapes.DOT_S1);
        assertEquals(191552L, j.get("1,4,5|1#0"), "S1: fork #191557 is the last Task of 1,4#1 - its line's join");
        assertEquals(191554L, j.get("1,6,7|1#0"), "S1: fork #191562 is the last Task of 1,6#1 - the top-level join");
        assertEquals(191554L, j.get("1,6,7,10,11,12|1|0|0|0#0"), "S1: five levels deep, every fork last in its chain");
    }

    @Test
    public void test_S2_elevenForksResolveToOneJoin() {
        final List<SegmentData.Line> lines = SegmentAlgebra.decompose(SegmentDotUtils.parse(SegmentFixtureShapes.DOT_S2.dot()));
        final Map<String, Long> j = SegmentAlgebra.derivedJoins(lines);
        final Set<Long> forks = lines.stream()
                .filter(l -> !l.isRoot() && Long.valueOf(191647L).equals(j.get(l.ctx())))
                .map(l -> Objects.requireNonNull(l.forkTaskId()))
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(Set.of(191646L, 191655L, 191659L, 191661L, 191663L, 191664L, 191668L, 191675L, 191682L, 191685L, 191687L), forks,
                "S2: eleven forks resolve to the one join #191647");
    }

    @Test
    public void test_S2_linesPerJoin() {
        final Map<Long, Long> perJoin = new TreeMap<>(joins(SegmentFixtureShapes.DOT_S2).values().stream()
                .collect(Collectors.groupingBy(x -> x, Collectors.counting())));
        assertEquals(Map.of(191645L, 3L, 191647L, 15L, 191658L, 1L, 191674L, 1L, 191681L, 1L), perJoin,
                "S2: lines resolving to each join");
    }

    @Test
    public void test_S5_graftUnderChainTail_joinIsEnclosingJoin() {
        assertEquals(11L, joins(SegmentFixtureShapes.DOT_S5).get("1,3,2|0#1"), "S5 (F1): the graft's join is the enclosing join");
    }

    @Test
    public void test_S6_innermostTailsSkipTwoLevels() {
        final Map<String, Long> j = joins(SegmentFixtureShapes.DOT_S6);
        assertEquals(2L, j.get("1,5,3,2|1|1#1"), "S6: level-3 line joins the top-level join");
        assertEquals(2L, j.get("1,5,3,2|1|1#2"), "S6: level-3 line joins the top-level join");
        assertEquals(2L, j.get("1,5,3|1#1"), "S6: level-2 line joins the top-level join");
        assertEquals(2L, j.get("1,5#2"), "S6: a level-1 line whose splitter produced no lines joins the top-level join");
    }

    @Test
    public void test_rootLineHasNoJoin() {
        final List<SegmentData.Line> lines = SegmentAlgebra.decompose(SegmentDotUtils.parse(SegmentFixtureShapes.DOT_S1.dot()));
        assertNull(SegmentAlgebra.derivedJoin(SegmentAlgebra.LineIndex.of(lines), lines.getFirst()), "the root line has no join");
        assertFalse(SegmentAlgebra.derivedJoins(lines).containsKey("1"));
    }

    private static void assertGolden(SegmentFixtureShapes.DotShape shape) {
        assertEquals(new TreeMap<>(SegmentGolden.load(shape.id()).derivedJoins()), new TreeMap<>(joins(shape)),
                shape.id() + ": derived joins must equal the joins read off today's graph (golden)");
    }

    @Test public void test_golden_S1() { assertGolden(SegmentFixtureShapes.DOT_S1); }
    @Test public void test_golden_S2() { assertGolden(SegmentFixtureShapes.DOT_S2); }
    @Test public void test_golden_S3() { assertGolden(SegmentFixtureShapes.DOT_S3); }
    @Test public void test_golden_S4() { assertGolden(SegmentFixtureShapes.DOT_S4); }
    @Test public void test_golden_S5() { assertGolden(SegmentFixtureShapes.DOT_S5); }
    @Test public void test_golden_S6() { assertGolden(SegmentFixtureShapes.DOT_S6); }
    @Test public void test_golden_S7() { assertGolden(SegmentFixtureShapes.DOT_S7); }
    @Test public void test_golden_S8() { assertGolden(SegmentFixtureShapes.DOT_S8); }
}

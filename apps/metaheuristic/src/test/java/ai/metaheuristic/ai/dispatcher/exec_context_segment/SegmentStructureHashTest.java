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

import ai.metaheuristic.ai.yaml.exec_context_segment.ExecContextSegmentParams;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.*;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 5 of 041-EXEC-CONTEXT-SEGMENTS-PLAN (section 8.5, decision 12): the per-segment structure hash and the root over
 * them. The roots of S1-S8 are pinned values of this very function (today's DOT code has no root): a changed canonical
 * form must change {@link SegmentStructureHash#ALGO}, and these pins are what catches a change that forgot to.
 */
@Execution(ExecutionMode.CONCURRENT)
public class SegmentStructureHashTest {

    private static final Map<String, String> ROOTS = Map.of(
            "S1", "25305220e58051b468bd870f7086be69fc2f29376138d563fbd36e0cb94c8801",
            "S2", "ac1c72f34af8c30d9811aa992569802fe5913b9d2f23efa9139ba38d9884ebf6",
            "S3", "9d67b52b3156f8335e00863fb97e3a2b4a4e8f390b026151e6156c0947aec01f",
            "S4", "d9e894eb17c8869b48c9b49b8a64b60868fe4594fa1a503ee43318928f8e6c37",
            "S5", "7e0c7b9faa2c167e08c230dc9ffa1e1dd984be85f3781bd6ea12c07bd09ea015",
            "S6", "bab3981d0978be432ae10c2e1685bcf2c29ebc8d8f391d23464288124b89a77a",
            "S7", "affe4685f0606d6fd83be170e9d0ceed23b4a42cffccbc78f0b74ee852e4d403",
            // S8 is S3's graph with S3's grouping - the same root
            "S8", "9d67b52b3156f8335e00863fb97e3a2b4a4e8f390b026151e6156c0947aec01f");

    private static List<SegmentData.Segment> segments(SegmentFixtureShapes.DotShape shape) {
        return SegmentAlgebra.segments(SegmentAlgebra.decompose(SegmentDotUtils.parse(shape.dot())), shape.startsSegment());
    }

    private static SegmentData.Segment segment(List<SegmentData.Segment> segments, String lineCtxId) {
        return segments.stream().filter(s -> s.lineCtxId().equals(lineCtxId)).findFirst().orElseThrow();
    }

    @Test
    public void test_algoIdIsConstant() {
        assertEquals("mh-segment-root-sha256-v1", SegmentStructureHash.ALGO);
    }

    @Test
    public void test_hashIs64LowercaseHex() {
        final String h = SegmentStructureHash.structureHash(segments(SegmentFixtureShapes.DOT_S1).getFirst());
        assertTrue(h.matches("[0-9a-f]{64}"), "a structure hash is 64 lowercase hex characters (MH_EXEC_CONTEXT_SEGMENT.STRUCTURE_HASH), was " + h);
    }

    @Test
    public void test_taskStateAndVariableStateEntries_leaveTheHashUnchanged() {
        final SegmentData.Segment seg = segment(segments(SegmentFixtureShapes.DOT_S1), "1,6#1");
        final ExecContextSegmentParams params = SegmentParamsConverter.params(seg.lines());
        final String before = SegmentStructureHash.structureHash(SegmentParamsConverter.segment(seg.lineCtxId(), seg.forkTaskId(), params));

        params.states.put(191559L, EnumsApi.TaskExecState.OK);
        params.states.put(191560L, EnumsApi.TaskExecState.ERROR);
        params.triesWasMade.put(191560L, 2);
        final ExecContextApiData.VariableState vs = new ExecContextApiData.VariableState();
        vs.taskId = 191559L;
        vs.taskContextId = "1,6#1";
        params.variableStates.add(vs);

        assertEquals(before, SegmentStructureHash.structureHash(SegmentParamsConverter.segment(seg.lineCtxId(), seg.forkTaskId(), params)),
                "task states, tries and variable-state entries are not structure");
    }

    /** Replaces the lines of the segment at {@code lineCtxId} with {@code change} applied to them. */
    private static List<SegmentData.Segment> changed(List<SegmentData.Segment> segments, String lineCtxId,
                                                     UnaryOperator<List<SegmentData.Line>> change) {
        return segments.stream()
                .map(s -> s.lineCtxId().equals(lineCtxId) ? new SegmentData.Segment(s.lineCtxId(), s.forkTaskId(), change.apply(s.lines())) : s)
                .toList();
    }

    private static List<SegmentData.Line> replaceLine(List<SegmentData.Line> lines, String ctx, UnaryOperator<SegmentData.Line> f) {
        return lines.stream().map(l -> l.ctx().equals(ctx) ? f.apply(l) : l).toList();
    }

    private static void assertStructuralChange(String what, UnaryOperator<List<SegmentData.Line>> change) {
        final List<SegmentData.Segment> base = segments(SegmentFixtureShapes.DOT_S1);
        final List<SegmentData.Segment> alt = changed(base, "1,6#1", change);
        assertNotEquals(SegmentStructureHash.structureHash(segment(base, "1,6#1")), SegmentStructureHash.structureHash(segment(alt, "1,6#1")),
                what + " must change the segment's hash");
        assertNotEquals(SegmentStructureHash.rootOf(base), SegmentStructureHash.rootOf(alt), what + " must change the root");
    }

    @Test
    public void test_changedTaskId_changesHashAndRoot() {
        assertStructuralChange("a changed Task id", lines -> replaceLine(lines, "1,6#1", l -> {
            final List<SegmentData.Vertex> t = new ArrayList<>(l.tasks());
            t.set(1, new SegmentData.Vertex(999L, null));
            return new SegmentData.Line(l.ctx(), l.forkTaskId(), t);
        }));
    }

    @Test
    public void test_changedCtx_changesHashAndRoot() {
        assertStructuralChange("a changed ctx", lines -> replaceLine(lines, "1,6,7,8|1|0#0",
                l -> new SegmentData.Line("1,6,7,8|1|0#1", l.forkTaskId(), l.tasks())));
    }

    @Test
    public void test_changedOrder_changesHashAndRoot() {
        assertStructuralChange("a changed chain order", lines -> replaceLine(lines, "1,6#1", l -> {
            final List<SegmentData.Vertex> t = new ArrayList<>(l.tasks());
            Collections.swap(t, 0, 1);
            return new SegmentData.Line(l.ctx(), l.forkTaskId(), t);
        }));
    }

    @Test
    public void test_changedTag_changesHashAndRoot() {
        assertStructuralChange("an added tag", lines -> replaceLine(lines, "1,6#1", l -> {
            final List<SegmentData.Vertex> t = new ArrayList<>(l.tasks());
            t.set(0, new SegmentData.Vertex(t.getFirst().taskId(), "terminal"));
            return new SegmentData.Line(l.ctx(), l.forkTaskId(), t);
        }));
    }

    @Test
    public void test_changedFork_changesHashAndRoot() {
        assertStructuralChange("a changed fork", lines -> replaceLine(lines, "1,6,7|1#0",
                l -> new SegmentData.Line(l.ctx(), 191561L, l.tasks())));
    }

    @Test
    public void test_permutedStorageOrder_leavesRootUnchanged() {
        final List<SegmentData.Segment> base = segments(SegmentFixtureShapes.DOT_S3);
        final String root = SegmentStructureHash.rootOf(base);
        for (long seed = 1; seed <= 20; seed++) {
            final Random rnd = new Random(seed);
            final List<SegmentData.Segment> shuffled = new ArrayList<>();
            for (SegmentData.Segment s : base) {
                final List<SegmentData.Line> lines = new ArrayList<>(s.lines());
                Collections.shuffle(lines, rnd);
                shuffled.add(new SegmentData.Segment(s.lineCtxId(), s.forkTaskId(), lines));
            }
            Collections.shuffle(shuffled, rnd);
            assertEquals(root, SegmentStructureHash.rootOf(shuffled), "seed " + seed + ": the order segments and lines are stored in must not matter");
        }
    }

    @Test
    public void test_divergent_namesTheTamperedSegment() {
        final List<SegmentData.Segment> segments = segments(SegmentFixtureShapes.DOT_S2);
        final List<SegmentStructureHash.SegmentHash> recomputed = segments.stream()
                .map(s -> new SegmentStructureHash.SegmentHash(s.lineCtxId(), SegmentStructureHash.structureHash(s))).toList();
        final List<SegmentStructureHash.SegmentHash> stored = recomputed.stream()
                .map(h -> h.lineCtxId().equals("1,6#1") ? new SegmentStructureHash.SegmentHash(h.lineCtxId(), "0".repeat(64)) : h)
                .toList();
        assertEquals(List.of("1,6#1"), SegmentStructureHash.divergent(stored, recomputed));
        assertNotEquals(SegmentStructureHash.root(stored), SegmentStructureHash.root(recomputed));
        assertEquals(List.of(), SegmentStructureHash.divergent(recomputed, recomputed));
    }

    private static void assertRoot(SegmentFixtureShapes.DotShape shape) {
        assertEquals(ROOTS.get(shape.id()), SegmentStructureHash.rootOf(segments(shape)), shape.id() + ": pinned root");
    }

    @Test public void test_root_S1() { assertRoot(SegmentFixtureShapes.DOT_S1); }
    @Test public void test_root_S2() { assertRoot(SegmentFixtureShapes.DOT_S2); }
    @Test public void test_root_S3() { assertRoot(SegmentFixtureShapes.DOT_S3); }
    @Test public void test_root_S4() { assertRoot(SegmentFixtureShapes.DOT_S4); }
    @Test public void test_root_S5() { assertRoot(SegmentFixtureShapes.DOT_S5); }
    @Test public void test_root_S6() { assertRoot(SegmentFixtureShapes.DOT_S6); }
    @Test public void test_root_S7() { assertRoot(SegmentFixtureShapes.DOT_S7); }
    @Test public void test_root_S8() { assertRoot(SegmentFixtureShapes.DOT_S8); }
}

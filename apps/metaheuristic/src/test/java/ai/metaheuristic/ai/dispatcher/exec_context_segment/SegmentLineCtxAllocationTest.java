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

import ai.metaheuristic.commons.utils.ContextUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4 of 041-EXEC-CONTEXT-SEGMENTS-PLAN (section 8.5, decision 10): lock-free line ctx allocation - the seed is the
 * highest existing sibling index, a new line takes {@code seed + rowId}.
 */
@Execution(ExecutionMode.CONCURRENT)
public class SegmentLineCtxAllocationTest {

    /** Every taskContextId of S3 (ExecContext 334): splitter-0 carries both 1,6#1 and 1,17#1..4. */
    private static Set<String> s3Ctx() {
        final Set<String> ctx = new TreeSet<>();
        SegmentDotUtils.parse(SegmentFixtureShapes.DOT_S3.dot()).nodes().values().forEach(n -> ctx.add(n.ctx()));
        return ctx;
    }

    @Test
    public void test_seedIsHighestExistingSiblingIndex() {
        assertEquals(4, SegmentLineCtx.seed("1,17", s3Ctx()), "S3: the manual base 1,17 has #1..#4");
        assertEquals(1, SegmentLineCtx.seed("1,6", s3Ctx()), "S3: the pipeline base 1,6 has #1");
        assertEquals(0, SegmentLineCtx.seed("1,99", s3Ctx()), "no sibling at the base -> seed 0");
        assertEquals(12, SegmentLineCtx.seed("1,2", List.of("1,2#3", "1,2#12", "1,2,5|1#40", "1,3#99")),
                "only ctx exactly at the base count, not deeper or other bases");
    }

    @Test
    public void test_allocatedIndexNeverCollidesWithAnExistingSibling() {
        final Set<String> existing = s3Ctx();
        final long seed = SegmentLineCtx.seed("1,17", existing);
        for (long rowId = 1; rowId <= 500; rowId++) {
            final String ctx = SegmentLineCtx.lineCtx("1,17", seed, rowId);
            assertFalse(existing.contains(ctx), "allocated " + ctx + " collides with an existing sibling");
            assertEquals("1,17", ContextUtils.getLevel(ctx));
        }
    }

    @Test
    public void test_indicesGrowWithRowId_andNeverRepeat() {
        final Set<String> seen = new HashSet<>();
        long prev = -1;
        for (long rowId : List.of(3L, 7L, 8L, 100L, 100_000L)) {
            final String ctx = SegmentLineCtx.lineCtx("1,17", 4, rowId);
            final long index = Long.parseLong(Objects.requireNonNull(ContextUtils.getPath(ctx)));
            assertEquals(4 + rowId, index);
            assertTrue(index > prev, "index must grow with the row id");
            assertTrue(seen.add(ctx), "no index repeats");
            prev = index;
        }
    }

    @Test
    public void test_twoBasesUnderOneFork_areIndependent() {
        final Set<String> existing = s3Ctx();
        final long seed17 = SegmentLineCtx.seed("1,17", existing);
        final long seed6 = SegmentLineCtx.seed("1,6", existing);
        assertEquals("1,17#" + (4 + 10), SegmentLineCtx.lineCtx("1,17", seed17, 10));
        assertEquals("1,6#" + (1 + 10), SegmentLineCtx.lineCtx("1,6", seed6, 10),
                "the same row id at another base gives an index from that base's own seed");
    }

    @Test
    public void test_nonPositiveRowId_rejected() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> SegmentLineCtx.lineCtx("1,17", 4, 0));
        assertTrue(e.getMessage().startsWith("01.910.020"));
    }
}

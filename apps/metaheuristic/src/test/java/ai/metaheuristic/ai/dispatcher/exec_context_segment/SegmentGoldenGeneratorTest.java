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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Keeps the committed goldens of 041-EXEC-CONTEXT-SEGMENTS-PLAN honest: each is what today's DOT implementation
 * answers for its shape ({@link SegmentGoldenGenerator}). When a golden file is missing, or
 * {@code -Dsegment.golden.write=true} is set, the file is written under {@code src/test/resources/segment/golden/};
 * otherwise the freshly generated golden must equal the committed one. Removed in Phase 21 with the DOT code.
 */
@Execution(ExecutionMode.CONCURRENT)
public class SegmentGoldenGeneratorTest {

    private static final int READY_STATES = 200;
    private static final Path GOLDEN_DIR = Path.of("src", "test", "resources", "segment", "golden");

    private static void check(SegmentFixtureShapes.DotShape shape, long seed, boolean withDescendants) throws Exception {
        final SegmentGolden.Golden generated = SegmentGoldenGenerator.generate(shape, READY_STATES, seed, withDescendants);
        final Path file = GOLDEN_DIR.resolve(shape.id() + ".json");
        if (!Files.exists(file) || Boolean.getBoolean("segment.golden.write")) {
            Files.createDirectories(GOLDEN_DIR);
            Files.writeString(file, SegmentGolden.toJson(generated), StandardCharsets.UTF_8);
            return;
        }
        final SegmentGolden.Golden committed = SegmentGolden.fromJson(Files.readString(file, StandardCharsets.UTF_8));
        assertEquals(committed, generated, shape.id() + ": today's DOT implementation no longer reproduces the committed golden");
    }

    @Test public void test_S1() throws Exception { check(SegmentFixtureShapes.DOT_S1, 41L, true); }
    @Test public void test_S2() throws Exception { check(SegmentFixtureShapes.DOT_S2, 42L, true); }
    @Test public void test_S3() throws Exception { check(SegmentFixtureShapes.DOT_S3, 43L, true); }
    @Test public void test_S4() throws Exception { check(SegmentFixtureShapes.DOT_S4, 44L, true); }
    @Test public void test_S5() throws Exception { check(SegmentFixtureShapes.DOT_S5, 45L, true); }
    @Test public void test_S6() throws Exception { check(SegmentFixtureShapes.DOT_S6, 46L, true); }
    @Test public void test_S7() throws Exception { check(SegmentFixtureShapes.DOT_S7, 47L, false); }
    @Test public void test_S8() throws Exception { check(SegmentFixtureShapes.DOT_S8, 48L, true); }
}

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

package ai.metaheuristic.ai.utils.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AssetUtils.getVariableCacheDir: {@code <dispatcherTempPath>/variable/v2/<power10>/<power7>/<power4>}.
 * The expected paths are written out literally, independent of DigitUtils.
 */
@Execution(ExecutionMode.CONCURRENT)
public class AssetUtilsVariableCacheDirTest {

    private static Path expected(Path dispatcherTempPath, String power10, String power7, String power4) {
        return dispatcherTempPath.resolve("variable").resolve("v2").resolve(power10).resolve(power7).resolve(power4);
    }

    @Test
    public void test_v2DirName() {
        assertEquals("v2", AssetUtils.VARIABLE_CACHE_V2_DIR);
    }

    @Test
    public void test_exampleOfTheTask() {
        final Path base = Path.of("mh-home", "dispatcher", "temp");
        assertEquals(expected(base, "0", "11", "2222"), AssetUtils.getVariableCacheDir(base, 112_222L));
    }

    @Test
    public void test_allThreeLevelsNonZero() {
        final Path base = Path.of("mh-home", "dispatcher", "temp");
        assertEquals(expected(base, "1", "2345", "6789"), AssetUtils.getVariableCacheDir(base, 123_456_789L));
    }

    @Test
    public void test_smallIdAndBoundaries() {
        final Path base = Path.of("mh-home", "dispatcher", "temp");
        assertEquals(expected(base, "0", "0", "42"), AssetUtils.getVariableCacheDir(base, 42L));
        assertEquals(expected(base, "0", "1", "0"), AssetUtils.getVariableCacheDir(base, 10_000L));
        assertEquals(expected(base, "0", "999", "9999"), AssetUtils.getVariableCacheDir(base, 9_999_999L));
        assertEquals(expected(base, "0", "1000", "0"), AssetUtils.getVariableCacheDir(base, 10_000_000L));
        assertEquals(expected(base, "1", "0", "0"), AssetUtils.getVariableCacheDir(base, 100_000_000L));
    }

    @Test
    public void test_createsNothingOnDisk(@TempDir Path tempDir) {
        final Path base = tempDir.resolve("dispatcher-temp");
        final Path dir = AssetUtils.getVariableCacheDir(base, 112_222L);
        assertEquals(expected(base, "0", "11", "2222"), dir);
        assertTrue(Files.notExists(base), "getVariableCacheDir must only compute the path, " + base + " was created");
    }
}

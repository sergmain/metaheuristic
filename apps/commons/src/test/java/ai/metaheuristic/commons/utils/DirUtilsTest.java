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

package ai.metaheuristic.commons.utils;

import ai.metaheuristic.commons.CommonConsts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sergio Lissner
 * Date: 11/26/2023
 * Time: 10:18 PM
 */
public class DirUtilsTest {

    @Test
    public void test_getParent_55() {
        assertEquals(Path.of("\\aaa"), DirUtils.getParent(Path.of("\\aaa\\bbbb\\ccc"), Path.of("bbbb\\ccc")));
        assertEquals(Path.of("/aaa"), DirUtils.getParent(Path.of("/aaa/bbbb/ccc"), Path.of("bbbb/ccc")));
        assertNull(DirUtils.getParent(Path.of("/aaa/bbbb/ccc/ddd"), Path.of("bbbb/ccc")));
    }

    @Test
    public void test_createMhTempPath_notNull(@TempDir Path tempDir) {
        Path result = DirUtils.createMhTempPath(tempDir, "test-prefix-");
        assertNotNull(result);
        assertTrue(Files.exists(result));
        assertTrue(Files.isDirectory(result));
    }

    @Test
    public void test_createMhTempPath_directoryStructure(@TempDir Path tempDir) {
        String prefix = "create-";
        Path result = DirUtils.createMhTempPath(tempDir, prefix);
        assertNotNull(result);

        String todayDate = LocalDate.now().toString();

        // verify mh-temp dir exists
        Path mhTempDir = tempDir.resolve(CommonConsts.METAHEURISTIC_TEMP);
        assertTrue(Files.exists(mhTempDir));

        // verify prefix dir exists: mh-temp/create-
        Path prefixDir = mhTempDir.resolve(prefix);
        assertTrue(Files.exists(prefixDir));

        // verify date dir exists: mh-temp/create-/2026-02-10
        Path dateDir = prefixDir.resolve(todayDate);
        assertTrue(Files.exists(dateDir));

        // verify result is under the date dir
        assertTrue(result.startsWith(dateDir));
    }

    @Test
    public void test_createMhTempPath_resultDirNameStartsWithPrefix(@TempDir Path tempDir) {
        String prefix = "batch-processing-";
        Path result = DirUtils.createMhTempPath(tempDir, prefix);
        assertNotNull(result);

        String dirName = result.getFileName().toString();
        assertTrue(dirName.startsWith(prefix), "Result dir name '" + dirName + "' should start with prefix '" + prefix + "'");
    }

    @Test
    public void test_createMhTempPath_multipleCalls_uniquePaths(@TempDir Path tempDir) {
        String prefix = "multi-";
        Path result1 = DirUtils.createMhTempPath(tempDir, prefix);
        Path result2 = DirUtils.createMhTempPath(tempDir, prefix);

        assertNotNull(result1);
        assertNotNull(result2);
        assertNotEquals(result1, result2);
    }

    @Test
    public void test_createMhTempPath_differentPrefixes(@TempDir Path tempDir) {
        Path result1 = DirUtils.createMhTempPath(tempDir, "alpha-");
        Path result2 = DirUtils.createMhTempPath(tempDir, "beta-");

        assertNotNull(result1);
        assertNotNull(result2);

        // verify they are under different prefix directories
        Path mhTempDir = tempDir.resolve(CommonConsts.METAHEURISTIC_TEMP);
        assertTrue(result1.startsWith(mhTempDir.resolve("alpha-")));
        assertTrue(result2.startsWith(mhTempDir.resolve("beta-")));
    }
}

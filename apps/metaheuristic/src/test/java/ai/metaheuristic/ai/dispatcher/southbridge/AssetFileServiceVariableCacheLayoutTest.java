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

package ai.metaheuristic.ai.dispatcher.southbridge;

import ai.metaheuristic.ai.Globals;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the Dispatcher's on-disk cache of a local Variable lives, observed through
 * {@link AssetFileService#deleteAssetFile(Long)} on a real filesystem.
 *
 * Every test puts a cached file at both layouts - the flat one
 * {@code <dispatcherTempPath>/variable/variable-<id>} and the three-level 'v2' one
 * {@code <dispatcherTempPath>/variable/v2/<power10>/<power7>/<power4>/variable-<id>} - and asserts which of
 * the two deleteAssetFile() removed: the one production resolves for that variableId.
 *
 * No double: deleteAssetFile() reads only Globals.dispatcherTempPath, so AssetFileService gets null for its
 * VariableTxService, which no path of this test reaches.
 */
@Execution(ExecutionMode.CONCURRENT)
public class AssetFileServiceVariableCacheLayoutTest {

    private static final String CONTENT = "CACHED-CONTENT";

    private static AssetFileService assetFileService(Path dispatcherTempPath) {
        final Globals globals = new Globals();
        globals.dispatcherTempPath = dispatcherTempPath;
        return new AssetFileService(globals, null);
    }

    private static Path flatFile(Path dispatcherTempPath, long variableId) {
        return dispatcherTempPath.resolve("variable").resolve("variable-" + variableId);
    }

    private static Path v2File(Path dispatcherTempPath, String power10, String power7, String power4, long variableId) {
        return dispatcherTempPath.resolve("variable").resolve("v2").resolve(power10).resolve(power7).resolve(power4)
            .resolve("variable-" + variableId);
    }

    @SneakyThrows
    private static Path cached(Path file) {
        Files.createDirectories(file.getParent());
        Files.writeString(file, CONTENT, StandardCharsets.UTF_8);
        assertTrue(Files.exists(file), "ARRANGE: " + file + " must exist");
        return file;
    }

    @Test
    public void test_deleteAssetFile_variable112222(@TempDir Path tempDir) {
        final Path dispatcherTempPath = tempDir.resolve("dispatcher-temp");
        final Path flat = cached(flatFile(dispatcherTempPath, 112222L));
        final Path v2 = cached(v2File(dispatcherTempPath, "0", "11", "2222", 112222L));

        assetFileService(dispatcherTempPath).deleteAssetFile(112222L);

        assertFalse(Files.exists(v2), "variable #112222 must be cached at the three-level v2 " + v2);
        assertTrue(Files.exists(flat), "the flat " + flat + " isn't the cache of variable #112222 any more");
    }

    @Test
    public void test_deleteAssetFile_variable123456789(@TempDir Path tempDir) {
        final Path dispatcherTempPath = tempDir.resolve("dispatcher-temp");
        final Path flat = cached(flatFile(dispatcherTempPath, 123456789L));
        final Path v2 = cached(v2File(dispatcherTempPath, "1", "2345", "6789", 123456789L));

        assetFileService(dispatcherTempPath).deleteAssetFile(123456789L);

        assertFalse(Files.exists(v2), "variable #123456789 must be cached at the three-level v2 " + v2);
        assertTrue(Files.exists(flat), "the flat " + flat + " isn't the cache of variable #123456789 any more");
    }

    @Test
    public void test_deleteAssetFile_variable42(@TempDir Path tempDir) {
        final Path dispatcherTempPath = tempDir.resolve("dispatcher-temp");
        final Path flat = cached(flatFile(dispatcherTempPath, 42L));
        final Path v2 = cached(v2File(dispatcherTempPath, "0", "0", "42", 42L));

        assetFileService(dispatcherTempPath).deleteAssetFile(42L);

        assertFalse(Files.exists(v2), "variable #42 must be cached at the three-level v2 " + v2);
        assertTrue(Files.exists(flat), "the flat " + flat + " isn't the cache of variable #42 any more");
    }

    @Test
    public void test_deleteAssetFile_variable10000000(@TempDir Path tempDir) {
        final Path dispatcherTempPath = tempDir.resolve("dispatcher-temp");
        final Path flat = cached(flatFile(dispatcherTempPath, 10_000_000L));
        final Path v2 = cached(v2File(dispatcherTempPath, "0", "1000", "0", 10_000_000L));

        assetFileService(dispatcherTempPath).deleteAssetFile(10_000_000L);

        assertFalse(Files.exists(v2), "variable #10000000 must be cached at the three-level v2 " + v2);
        assertTrue(Files.exists(flat), "the flat " + flat + " isn't the cache of variable #10000000 any more");
    }
}

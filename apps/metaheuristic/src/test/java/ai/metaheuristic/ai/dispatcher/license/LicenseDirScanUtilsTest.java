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

package ai.metaheuristic.ai.dispatcher.license;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * What the license directory yields. Spring-less on purpose — the scan needs a Path and nothing
 * else, which is the whole reason it was pulled out of LicenseTokenSupplier.
 *
 * The tokens here are not real JWSs and do not need to be: this class decides WHICH bytes reach
 * the verify path, never whether they verify.
 *
 * @author Serge
 */
@Execution(CONCURRENT)
public class LicenseDirScanUtilsTest {

    private static void write(Path dir, String name, String content) throws IOException {
        Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8);
    }

    @Test
    public void test_missingDir_isEmpty(@TempDir Path tmp) {
        // an install with no license directory boots and reports NO_LICENSE; it does not fail.
        assertTrue(LicenseDirScanUtils.scanDir(tmp.resolve("nope")).isEmpty());
    }

    @Test
    public void test_pathIsAFileNotADir_isEmpty(@TempDir Path tmp) throws IOException {
        final Path f = tmp.resolve("license");
        Files.writeString(f, "not-a-dir", StandardCharsets.UTF_8);

        assertTrue(LicenseDirScanUtils.scanDir(f).isEmpty());
    }

    @Test
    public void test_emptyDir_isEmpty(@TempDir Path tmp) {
        assertTrue(LicenseDirScanUtils.scanDir(tmp).isEmpty());
    }

    @Test
    public void test_readsEveryJwsFile(@TempDir Path tmp) throws IOException {
        write(tmp, "a.jws", "token-A");
        write(tmp, "b.jws", "token-B");

        assertEquals(List.of("token-A", "token-B"), LicenseDirScanUtils.scanDir(tmp));
    }

    @Test
    public void test_onlyJwsSuffixIsRead(@TempDir Path tmp) throws IOException {
        write(tmp, "live.jws", "token-A");
        write(tmp, "notes.txt", "token-B");
        write(tmp, "old.jws.bak", "token-C");
        write(tmp, "README", "token-D");

        assertEquals(List.of("token-A"), LicenseDirScanUtils.scanDir(tmp));
    }

    @Test
    public void test_orderIsByFileName(@TempDir Path tmp) throws IOException {
        // the admin breakdown must not shuffle between reads.
        write(tmp, "c.jws", "token-C");
        write(tmp, "a.jws", "token-A");
        write(tmp, "b.jws", "token-B");

        assertEquals(List.of("token-A", "token-B", "token-C"), LicenseDirScanUtils.scanDir(tmp));
    }

    @Test
    public void test_trailingWhitespaceIsStripped(@TempDir Path tmp) throws IOException {
        // a compact JWS carries no whitespace; an editor's trailing newline is the same license,
        // and keeping it would make one grant look like two rows.
        write(tmp, "a.jws", "token-A\n");
        write(tmp, "b.jws", "  token-B\r\n");

        assertEquals(List.of("token-A", "token-B"), LicenseDirScanUtils.scanDir(tmp));
    }

    @Test
    public void test_blankFileIsSkipped(@TempDir Path tmp) throws IOException {
        write(tmp, "a.jws", "token-A");
        write(tmp, "empty.jws", "");
        write(tmp, "spaces.jws", "   \n  ");

        assertEquals(List.of("token-A"), LicenseDirScanUtils.scanDir(tmp));
    }

    @Test
    public void test_subdirectoryNamedLikeALicenseIsIgnored(@TempDir Path tmp) throws IOException {
        Files.createDirectory(tmp.resolve("archive.jws"));
        write(tmp, "a.jws", "token-A");

        assertEquals(List.of("token-A"), LicenseDirScanUtils.scanDir(tmp));
    }

    @Test
    public void test_notRecursive(@TempDir Path tmp) throws IOException {
        final Path sub = Files.createDirectory(tmp.resolve("sub"));
        write(sub, "deep.jws", "token-DEEP");
        write(tmp, "a.jws", "token-A");

        assertEquals(List.of("token-A"), LicenseDirScanUtils.scanDir(tmp));
    }

    @Test
    public void test_oneUnreadableFileDoesNotCostTheOthers(@TempDir Path tmp) throws IOException {
        write(tmp, "a.jws", "token-A");
        final Path bad = tmp.resolve("bad.jws");
        Files.writeString(bad, "token-BAD", StandardCharsets.UTF_8);
        write(tmp, "z.jws", "token-Z");

        // a file whose bytes are not valid UTF-8 fails readString; the scan must carry on.
        Files.write(bad, new byte[]{(byte) 0xC3, (byte) 0x28});

        final List<String> tokens = LicenseDirScanUtils.scanDir(tmp);

        assertTrue(tokens.contains("token-A"), tokens.toString());
        assertTrue(tokens.contains("token-Z"), tokens.toString());
        assertFalse(tokens.contains("token-BAD"), tokens.toString());
    }
}

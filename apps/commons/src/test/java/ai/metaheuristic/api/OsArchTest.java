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

package ai.metaheuristic.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static ai.metaheuristic.api.EnumsApi.OsArch;
import static org.junit.jupiter.api.Assertions.*;

@Execution(ExecutionMode.CONCURRENT)
public class OsArchTest {

    @Test
    public void test_normalize_linux() {
        assertEquals(OsArch.linux_amd64, OsArch.normalize("Linux", "amd64"));
        assertEquals(OsArch.linux_amd64, OsArch.normalize("Linux", "x86_64"));
        assertEquals(OsArch.linux_arm64, OsArch.normalize("Linux", "aarch64"));
        assertEquals(OsArch.linux_arm64, OsArch.normalize("Linux", "arm64"));
    }

    @Test
    public void test_normalize_windows() {
        assertEquals(OsArch.windows_amd64, OsArch.normalize("Windows 11", "amd64"));
        assertEquals(OsArch.windows_amd64, OsArch.normalize("Windows 10", "x86_64"));
        assertEquals(OsArch.windows_arm64, OsArch.normalize("Windows 11", "aarch64"));
    }

    @Test
    public void test_normalize_macos_darwin() {
        assertEquals(OsArch.darwin_amd64, OsArch.normalize("Mac OS X", "x86_64"));
        assertEquals(OsArch.darwin_arm64, OsArch.normalize("Mac OS X", "aarch64"));
        assertEquals(OsArch.darwin_arm64, OsArch.normalize("Darwin", "arm64"));
    }

    @Test
    public void test_normalize_unsupported_throws() {
        assertThrows(IllegalStateException.class, () -> OsArch.normalize("Plan9", "sparc"));
        // recognized OS but unsupported arch -> still no matching constant
        assertThrows(IllegalStateException.class, () -> OsArch.normalize("Linux", "ppc64le"));
    }

    @Test
    public void test_key_isName_andRoundTrips() {
        for (OsArch v : OsArch.values()) {
            assertEquals(v.name(), v.key());
            assertEquals(v, OsArch.fromKey(v.key()));
        }
        // underscore key shape confirmed (e.g. linux_amd64)
        assertEquals(OsArch.linux_amd64, OsArch.fromKey("linux_amd64"));
    }

    @Test
    public void test_fromKey_unknown_isNull() {
        assertNull(OsArch.fromKey(null));
        assertNull(OsArch.fromKey("nope"));
        // the OS-agnostic fallback key is NOT an OsArch
        assertNull(OsArch.fromKey("mh-default"));
    }

    @Test
    public void test_detect_resolvesCurrentJvm() {
        // smoke: detection of the running JVM must yield a supported constant
        OsArch current = OsArch.detect();
        assertNotNull(current);
        assertEquals(OsArch.normalize(System.getProperty("os.name", ""), System.getProperty("os.arch", "")), current);
    }
}

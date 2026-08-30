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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.exceptions.LicenseInstallationMirrorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * WHERE the installation-id mirror file is written.
 *
 * <p>Spring-less: {@link LicenseInstallationService#mirrorToFile} is driven directly over a temp
 * {@code mh.home}. The database is not the subject here - the identity is authoritative wherever it
 * comes from, and what is being pinned is the path the mirror lands on.
 *
 * <p>❗ These tests deliberately do NOT go through {@code installationId(...)}.
 * {@code LicenseInstallationService.cached} is a process-wide static with no reset hook, and the
 * consumer that writes the mirror runs only on the call that populates it. Routed through
 * {@code installationId}, whichever test arrived first wrote a file and the rest silently did
 * nothing - so at least one assertion failed on every run, and which one varied with the
 * {@code CONCURRENT} scheduling. {@code mirrorToFile} is the unit these tests are about; the cache
 * is not.
 *
 * @author Serge
 */
@Execution(CONCURRENT)
public class LicenseInstallationMirrorFileTest {

    private static final String ID = "3f2c1a90-1111-2222-3333-444444444444";

    private record Home(Path home, Path config, Path dispatcher) {

        Path homeFile() {
            return home.resolve(LicenseInstallationService.INSTALLATION_ID_FILE);
        }

        Path configFile() {
            return config.resolve(LicenseInstallationService.INSTALLATION_ID_FILE);
        }

        Path dispatcherFile() {
            return dispatcher.resolve(LicenseInstallationService.INSTALLATION_ID_FILE);
        }

        Globals globals() {
            final Globals globals = new Globals();
            globals.home = home;
            globals.dispatcherPath = dispatcher;
            return globals;
        }
    }

    private static Home tempHome() throws IOException {
        final Path home = Files.createTempDirectory("mh-home-");
        // Globals.postConstruct() creates {mh.home}/dispatcher before anything can write into it.
        final Path dispatcher = Files.createDirectories(home.resolve("dispatcher"));
        return new Home(home, home.resolve("config"), dispatcher);
    }

    @Test
    public void test_mirrorFile_landsAtTheHomeRoot_neverUnderConfig() throws IOException {
        // config/ is READ-ONLY configuration - an air-gapped or container deployment may mount it
        // that way, and a dispatcher that mints its identity into it writes where it was told not
        // to. The mirror belongs at the {mh.home} root, where an operator reads it off the box.
        final Home h = tempHome();

        LicenseInstallationService.mirrorToFile(h.globals(), ID);

        assertTrue(Files.exists(h.homeFile()));
        assertFalse(Files.exists(h.configFile()));
        assertFalse(Files.exists(h.dispatcherFile()));
    }

    @Test
    public void test_mirrorFile_holdsTheAuthoritativeId() throws IOException {
        final Home h = tempHome();

        LicenseInstallationService.mirrorToFile(h.globals(), ID);

        assertEquals(ID, Files.readString(h.homeFile(), StandardCharsets.UTF_8).strip());
    }

    @Test
    public void test_agreeingMirrorFile_isNotRewritten() throws IOException {
        // decideMirror says LEAVE, so an unwritable dir on a later boot is not a failure path.
        final Home h = tempHome();
        Files.writeString(h.homeFile(), ID, StandardCharsets.UTF_8);
        final long before = Files.getLastModifiedTime(h.homeFile()).toMillis();

        LicenseInstallationService.mirrorToFile(h.globals(), ID);

        assertEquals(before, Files.getLastModifiedTime(h.homeFile()).toMillis());
    }

    @Test
    public void test_disagreeingMirrorFile_isRewritten() throws IOException {
        // decideMirror says WRITE: the database is authoritative and a stale file loses. Without
        // this, test_agreeingMirrorFile_isNotRewritten passes just as well against a mirrorToFile
        // that never writes anything at all.
        final Home h = tempHome();
        Files.writeString(h.homeFile(), "00000000-dead-beef-0000-000000000000", StandardCharsets.UTF_8);

        LicenseInstallationService.mirrorToFile(h.globals(), ID);

        assertEquals(ID, Files.readString(h.homeFile(), StandardCharsets.UTF_8).strip());
    }

    @Test
    public void test_unwritableMirrorDir_failsTheCall() throws IOException {
        // ❗ A failed mirror write is FATAL - it raises rather than being logged and stepped over,
        // so the identity is not handed out unless the file beside it exists. A read-only mh.home
        // is no longer a deployment mirrorToFile tolerates.
        final Path parent = Files.createTempDirectory("mh-home-");
        final Globals globals = new Globals();
        // a FILE where the directory should be: creating anything under it must fail.
        globals.home = Files.createFile(parent.resolve("home"));
        globals.dispatcherPath = globals.home.resolve("dispatcher");

        final LicenseInstallationMirrorException e = assertThrows(LicenseInstallationMirrorException.class,
                () -> LicenseInstallationService.mirrorToFile(globals, ID));
        assertNotNull(e.getCause(), "the underlying IOException is carried, not flattened into the message");
    }
}

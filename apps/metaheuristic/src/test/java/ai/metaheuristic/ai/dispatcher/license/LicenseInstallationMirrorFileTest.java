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
 * <p>Spring-less: the service is constructed directly over a temp {@code mh.home} and a TX service
 * that answers with a fixed id. The database is not the subject here - the identity is
 * authoritative wherever it comes from, and what is being pinned is the path the mirror lands on.
 *
 * @author Serge
 */
@Execution(CONCURRENT)
public class LicenseInstallationMirrorFileTest {

    private static final String ID = "3f2c1a90-1111-2222-3333-444444444444";

    private record Home(Path home, Path config, Path dispatcher) {

        Path configFile() {
            return config.resolve(LicenseInstallationService.INSTALLATION_ID_FILE);
        }

        Path dispatcherFile() {
            return dispatcher.resolve(LicenseInstallationService.INSTALLATION_ID_FILE);
        }
    }

    private static Home tempHome() throws IOException {
        final Path home = Files.createTempDirectory("mh-home-");
        // Globals.postConstruct() creates {mh.home}/dispatcher before anything can write into it.
        final Path dispatcher = Files.createDirectories(home.resolve("dispatcher"));
        return new Home(home, home.resolve("config"), dispatcher);
    }

    /** The TX service with no database behind it; only its answer matters to the mirror. */
    private static LicenseInstallationService serviceOn(Home h) {
        final Globals globals = new Globals();
        globals.home = h.home();
        globals.dispatcherPath = h.dispatcher();

        return new LicenseInstallationService(globals, new LicenseInstallationTxService(null) {
            @Override
            public String getOrCreateInstallationId() {
                return ID;
            }
        });
    }

    @Test
    public void test_mirrorFile_landsUnderTheDispatcherDir_neverUnderConfig() throws IOException {
        // config/ is READ-ONLY configuration - an air-gapped or container deployment may mount it
        // that way, and a dispatcher that mints its identity into it writes where it was told not
        // to. Everything the dispatcher generates about itself belongs under {mh.home}/dispatcher.
        final Home h = tempHome();

        assertEquals(ID, serviceOn(h).installationId());

        assertTrue(Files.exists(h.dispatcherFile()));
        assertFalse(Files.exists(h.configFile()));
    }

    @Test
    public void test_mirrorFile_holdsTheAuthoritativeId() throws IOException {
        final Home h = tempHome();

        serviceOn(h).installationId();

        final Path written = Files.exists(h.dispatcherFile()) ? h.dispatcherFile() : h.configFile();
        assertEquals(ID, Files.readString(written, StandardCharsets.UTF_8).strip());
    }

    @Test
    public void test_agreeingMirrorFile_isNotRewritten() throws IOException {
        // decideMirror says LEAVE, so an unwritable dir on a later boot is not a failure path.
        final Home h = tempHome();
        Files.writeString(h.dispatcherFile(), ID, StandardCharsets.UTF_8);
        final long before = Files.getLastModifiedTime(h.dispatcherFile()).toMillis();

        serviceOn(h).installationId();

        assertEquals(before, Files.getLastModifiedTime(h.dispatcherFile()).toMillis());
    }

    @Test
    public void test_unwritableMirrorDir_doesNotFailTheCall() throws IOException {
        // a read-only filesystem is a supported deployment: the id still comes back, from the
        // database value, and the failure is a log line rather than a dead dispatcher.
        final Path home = Files.createTempDirectory("mh-home-");
        final Globals globals = new Globals();
        globals.home = home;
        // a FILE where the directory should be: creating anything under it must fail.
        globals.dispatcherPath = Files.createFile(home.resolve("dispatcher"));

        final LicenseInstallationService service =
                new LicenseInstallationService(globals, new LicenseInstallationTxService(null) {
                    @Override
                    public String getOrCreateInstallationId() {
                        return ID;
                    }
                });

        assertEquals(ID, service.installationId());
    }
}

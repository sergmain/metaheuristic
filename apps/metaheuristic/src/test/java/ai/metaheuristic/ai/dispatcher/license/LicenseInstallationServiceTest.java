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

import static ai.metaheuristic.ai.dispatcher.license.LicenseInstallationService.INSTALLATION_ID_FILE;
import static ai.metaheuristic.ai.dispatcher.license.LicenseInstallationService.mirrorToFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * {@link LicenseInstallationService#mirrorToFile} driven directly.
 *
 * <p>Spring-less: the method's only inputs are a {@code mh.home} and a string, and its only output
 * is a file, so every assertion below reads the real filesystem. Nothing here goes through
 * {@code installationId()} - the caching in front of it would let only the first call in the JVM
 * reach the mirror, so each case gets its own temp home and its own direct call.
 *
 * <p>{@code LicenseInstallationMirrorFileTest} covers the same method through the public entry
 * point; this one covers the decisions inside it.
 *
 * @author Serge
 */
@Execution(CONCURRENT)
public class LicenseInstallationServiceTest {

    private static final String ID = "3f2c1a90-1111-2222-3333-444444444444";
    private static final String OTHER_ID = "8b7e0000-9999-8888-7777-666666666666";

    /** A {@code Globals} over a fresh temp {@code mh.home}; only getHome() is ever reached. */
    private static Globals globalsOn(Path home) {
        final Globals globals = new Globals();
        globals.home = home;
        return globals;
    }

    private static Path tempHome() throws IOException {
        return Files.createTempDirectory("mh-home-");
    }

    private static Path mirror(Path home) {
        return home.resolve(INSTALLATION_ID_FILE);
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    public void test_missingFile_isWritten() throws IOException {
        final Path home = tempHome();

        mirrorToFile(globalsOn(home), ID);

        assertTrue(Files.exists(mirror(home)), "the mirror must be created when it is absent");
        assertEquals(ID, read(mirror(home)));
    }

    @Test
    public void test_writtenFile_holdsTheIdVerbatim_noTrailingNewline() throws IOException {
        // an operator reads this off the box by hand and pastes it into a licence request, so
        // whatever decoration is added here has to be stripped by whoever consumes it.
        final Path home = tempHome();

        mirrorToFile(globalsOn(home), ID);

        assertEquals(ID, read(mirror(home)), "the file is the id and nothing else");
    }

    @Test
    public void test_landsAtTheHomeRoot_neverUnderConfigOrDispatcher() throws IOException {
        // config/ is CONFIGURATION the operator supplies and deployments may mount it read-only;
        // minting an identity into it is the dispatcher writing to its own input.
        final Path home = tempHome();
        Files.createDirectories(home.resolve("config"));
        Files.createDirectories(home.resolve("dispatcher"));

        mirrorToFile(globalsOn(home), ID);

        assertTrue(Files.exists(mirror(home)));
        assertFalse(Files.exists(home.resolve("config").resolve(INSTALLATION_ID_FILE)));
        assertFalse(Files.exists(home.resolve("dispatcher").resolve(INSTALLATION_ID_FILE)));
    }

    @Test
    public void test_missingHomeDir_isCreated() throws IOException {
        // Files.createDirectories(dir) is what makes a first boot on an empty volume work.
        final Path home = tempHome().resolve("not-created-yet");
        assertFalse(Files.exists(home));

        mirrorToFile(globalsOn(home), ID);

        assertTrue(Files.isDirectory(home), "the home dir must be created rather than reported missing");
        assertEquals(ID, read(mirror(home)));
    }

    @Test
    public void test_agreeingFile_isLeftByteForByte() throws IOException {
        // decideMirror says LEAVE, so the file is not rewritten on every boot. A trailing newline
        // from an editor is not a disagreement, and surviving it is what proves nothing was
        // rewritten - a rewrite would normalise the file to the bare id.
        final Path home = tempHome();
        Files.writeString(mirror(home), ID + "\n", StandardCharsets.UTF_8);

        mirrorToFile(globalsOn(home), ID);

        assertEquals(ID + "\n", read(mirror(home)), "an agreeing file must not be rewritten");
    }

    @Test
    public void test_disagreeingFile_isOverwritten_databaseWins() throws IOException {
        // the file is never adopted: anyone who can write a text file could otherwise re-point
        // this installation's identity.
        final Path home = tempHome();
        Files.writeString(mirror(home), OTHER_ID, StandardCharsets.UTF_8);

        mirrorToFile(globalsOn(home), ID);

        assertEquals(ID, read(mirror(home)), "the authoritative id must replace the file's value");
    }

    @Test
    public void test_blankFile_isOverwritten() throws IOException {
        final Path home = tempHome();
        Files.writeString(mirror(home), "   \n ", StandardCharsets.UTF_8);

        mirrorToFile(globalsOn(home), ID);

        assertEquals(ID, read(mirror(home)));
    }

    @Test
    public void test_emptyFile_isOverwritten() throws IOException {
        final Path home = tempHome();
        Files.writeString(mirror(home), "", StandardCharsets.UTF_8);

        mirrorToFile(globalsOn(home), ID);

        assertEquals(ID, read(mirror(home)));
    }

    @Test
    public void test_unwritableHome_isSwallowed() throws IOException {
        // a read-only filesystem is a supported deployment: the mirror grants nothing, so failing
        // to write it must not take out a dispatcher. A FILE where the directory should be makes
        // createDirectories throw.
        final Path home = Files.createFile(tempHome().resolve("home"));

        assertDoesNotThrow(() -> mirrorToFile(globalsOn(home), ID));

        assertTrue(Files.isRegularFile(home), "the fixture must still be a file, not a dir");
    }

    @Test
    public void test_unsetHome_isSwallowed() {
        // getHome() throws IllegalArgumentException when mh.home was never bound; that is a
        // RuntimeException inside the try, and the catch covers RuntimeException for this reason.
        assertDoesNotThrow(() -> mirrorToFile(new Globals(), ID));
    }
}

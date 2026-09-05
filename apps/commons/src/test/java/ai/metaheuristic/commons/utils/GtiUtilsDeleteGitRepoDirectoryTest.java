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

package ai.metaheuristic.commons.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins that {@link GtiUtils#deleteGitRepoDirectory(Path)} removes a tree containing the read-only
 * files git writes under {@code .git/objects/pack/}.
 *
 * <p>❗ This test CANNOT fail on a POSIX box, and that is stated here rather than left to be
 * discovered. The defect it guards is the DOS read-only ATTRIBUTE: on Windows {@code Files.delete}
 * refuses a file carrying it, which is why a second bundle import could never re-clone the delivery
 * repo. POSIX has no such attribute - unlinking depends on the parent directory's write permission,
 * not the file's - so a read-only file here deletes cleanly whether or not the production code passes
 * {@code OVERRIDE_READ_ONLY}. The characterization Red for this bug is only reachable on Windows, and
 * it was taken there: {@code mh_import_bundle_from_git} answered with the pack index's path until the
 * option was added.
 *
 * <p>⚠️ An earlier version of this test made the pack DIRECTORY unwritable instead, which does fail on
 * POSIX. That was a false Red: it fails with the fix in place as well, because
 * {@code OVERRIDE_READ_ONLY} clears read-only on files and not on directories - and git never marks a
 * directory read-only anyway. Do not reintroduce it.
 *
 * <p>What the test is worth: it runs the real deletion against the real shape of a delivery clone, so
 * it holds on Windows CI, and it fails everywhere if the method is ever changed to something that
 * can't handle protected files at all.
 *
 * @author Serge
 * Date: 9/5/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class GtiUtilsDeleteGitRepoDirectoryTest {

    /**
     * A stand-in for a delivery clone. The clone dir sits INSIDE a dir this test owns, mirroring
     * production, where the clone is 'git-repo' under a per-repo dir MH created: commons-io temporarily
     * relaxes the permissions of the parent of what it deletes, and a root-owned parent such as /tmp
     * itself would fail for a reason that has nothing to do with what is being pinned here.
     */
    private static Path prepareCloneWithReadOnlyPackFile() throws IOException {
        final Path base = Files.createTempDirectory("mh-git-delivery-");
        final Path root = Files.createDirectories(base.resolve("git-repo"));
        final Path pack = Files.createDirectories(root.resolve(".git").resolve("objects").resolve("pack"));
        final Path idx = pack.resolve("pack-678ee34ea57af6c693ef5804c0278f65703d0498.idx");
        Files.writeString(idx, "not a real pack index");
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(idx, Set.of(PosixFilePermission.OWNER_READ));
        }
        else {
            Files.setAttribute(idx, "dos:readonly", Boolean.TRUE);
        }
        return root;
    }

    @Test
    public void test_deleteGitRepoDirectoryRemovesCloneWithReadOnlyPackFile() throws IOException {
        final Path root = prepareCloneWithReadOnlyPackFile();

        GtiUtils.deleteGitRepoDirectory(root);

        assertFalse(Files.exists(root),
                "the delivery clone must be gone, otherwise the next import can't re-clone into it: " + root);
    }

    /** The method is the one place that decides this, so an absent tree must not blow up differently. */
    @Test
    public void test_deleteGitRepoDirectoryOnAbsentDirectoryThrowsNoSuchFile() throws IOException {
        final Path base = Files.createTempDirectory("mh-git-delivery-absent-");
        final Path root = base.resolve("git-repo");
        assertFalse(Files.exists(root));

        assertThrows(IOException.class, () -> GtiUtils.deleteGitRepoDirectory(root));
    }
}

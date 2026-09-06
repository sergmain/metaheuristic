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

import ai.metaheuristic.api.data.BundleData;
import ai.metaheuristic.api.sourcing.GitInfo;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins that an import supplied with a fresh base directory clones cleanly, and does so REPEATEDLY.
 *
 * <p>{@link BundleUtils#initRepo} deletes an existing clone before re-cloning. That delete is the step
 * that made a dispatcher-side import non-repeatable: the clone used to land in a persistent per-repo
 * directory, so the second import of a repo depended on removing the first one's clone - and git's
 * read-only pack files defeat that on Windows. The fix moved the clone into the import's own temp dir,
 * so the deleting branch is never reached in the first place.
 *
 * <p>Both shapes are exercised here against a local origin repo, so nothing touches the network:
 * a fresh base dir per import (what the dispatcher now does), and a reused one (what it used to do,
 * still the CLI's shape, and still reached by a retry).
 *
 * @author Serge
 * Date: 9/5/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class BundleUtilsInitRepoTest {

    /** A real git repo on disk, so `git clone` has something to talk to without a network. */
    private static Path createOriginRepo() throws Exception {
        final Path origin = Files.createTempDirectory("mh-origin-");
        try (Git git = Git.init().setDirectory(origin.toFile()).setInitialBranch("master").call()) {
            Files.writeString(origin.resolve("mh-bundle.yaml"), "bundleConfig: []\nversion: 1\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("init").setSign(false).call();
        }
        return origin;
    }

    private static GitInfo gitInfo(Path origin) {
        final GitInfo gi = new GitInfo();
        gi.repo = origin.toUri().toString();
        gi.branch = "master";
        gi.commit = "HEAD";
        gi.path = null;
        return gi;
    }

    private static void assertCloned(BundleData.Cfg cfg) {
        assertNotNull(cfg.repoDir, "initRepo must publish the checked-out location");
        assertTrue(Files.isDirectory(cfg.repoDir), "clone dir missing: " + cfg.repoDir);
        assertTrue(Files.exists(cfg.repoDir.resolve("mh-bundle.yaml")),
                "the repo's content wasn't checked out into " + cfg.repoDir);
    }

    /**
     * The shape the dispatcher now uses: every import gets its own base dir, so no import ever has to
     * remove another's clone. This is what makes the operation repeatable.
     */
    @Test
    public void test_initRepoIntoAFreshBaseDirSucceedsEveryTime() throws Exception {
        final Path origin = createOriginRepo();

        for (int i = 0; i < 3; i++) {
            final Path base = Files.createTempDirectory("mh-import-" + i + "-");
            final BundleData.Cfg cfg = new BundleData.Cfg(null, base, gitInfo(origin));

            BundleUtils.initRepo(cfg);

            assertCloned(cfg);
            assertEquals(base, cfg.repoDir.getParent().getParent(),
                    "the clone must stay inside the base dir it was given, import #" + i);
        }
    }

    /** A reused base dir still has to work - it is the CLI's shape, and a retry's. */
    @Test
    public void test_initRepoIntoTheSameBaseDirTwiceReClones() throws Exception {
        final Path origin = createOriginRepo();
        final Path base = Files.createTempDirectory("mh-import-reused-");

        final BundleData.Cfg first = new BundleData.Cfg(null, base, gitInfo(origin));
        BundleUtils.initRepo(first);
        assertCloned(first);
        Files.writeString(first.repoDir.resolve("left-over.txt"), "from the first clone");

        final BundleData.Cfg second = new BundleData.Cfg(null, base, gitInfo(origin));
        BundleUtils.initRepo(second);

        assertCloned(second);
        assertEquals(first.repoDir, second.repoDir, "same base dir means the same clone location");
        assertFalse(Files.exists(second.repoDir.resolve("left-over.txt")),
                "the previous clone must be gone, not merged into: " + second.repoDir);
    }

    /** The dispatcher deletes the whole import tree afterwards, clone included. */
    @Test
    public void test_theWholeImportTreeIncludingTheCloneCanBeRemoved() throws Exception {
        final Path origin = createOriginRepo();
        // the import tree sits inside a dir this test owns, mirroring MH's temp tree: OVERRIDE_READ_ONLY
        // relaxes the permissions of the deleted dir's parent, and /tmp itself is not ours to touch
        final Path tempRoot = Files.createTempDirectory("mh-import-root-");
        final Path importDir = Files.createDirectories(tempRoot.resolve("bundle-1"));
        final Path base = Files.createDirectories(importDir.resolve("git-repo"));

        final BundleData.Cfg cfg = new BundleData.Cfg(null, base, gitInfo(origin));
        BundleUtils.initRepo(cfg);
        assertCloned(cfg);

        GtiUtils.deleteGitRepoDirectory(importDir);

        assertFalse(Files.exists(importDir), "an import must leave nothing behind: " + importDir);
    }

    /** A base dir that can't be reached is an error, not a silent empty clone. */
    @Test
    public void test_initRepoOnUnreachableRepoDoesNotPublishACloneDir() throws Exception {
        final Path base = Files.createTempDirectory("mh-import-bad-");
        final GitInfo gi = new GitInfo();
        gi.repo = base.resolve("no-such-repo-here").toUri().toString();
        gi.branch = "master";
        gi.commit = "HEAD";

        final BundleData.Cfg cfg = new BundleData.Cfg(null, base, gi);

        assertThrows(Throwable.class, () -> BundleUtils.initRepo(cfg));
    }

    /** initRepo refuses a Cfg with no git coordinates rather than cloning something arbitrary. */
    @Test
    public void test_initRepoWithoutGitInfoIsRejected() throws IOException {
        final Path base = Files.createTempDirectory("mh-import-nogit-");
        final BundleData.Cfg cfg = new BundleData.Cfg(null, base, null);

        assertThrows(Throwable.class, () -> BundleUtils.initRepo(cfg));
    }
}

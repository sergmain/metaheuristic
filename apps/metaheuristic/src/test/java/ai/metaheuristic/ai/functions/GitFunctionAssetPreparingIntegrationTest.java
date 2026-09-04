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

package ai.metaheuristic.ai.functions;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.utils.GitCommitCache;
import ai.metaheuristic.commons.utils.StrUtils;
import org.awaitility.Awaitility;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static ai.metaheuristic.ai.functions.FunctionEnums.DownloadPriority.NORMAL;
import static ai.metaheuristic.ai.functions.FunctionRepositoryData.DownloadGitFunctionTask;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end asset preparation for a git-sourced external Function: a Task arrives pinned to a commit,
 * the Processor materializes that commit, and readiness is reported.
 *
 * <p>Everything the scenario depends on is real: the git binary, a repo with real commits built by JGit
 * in a temp dir, the MultiTenantedQueue with its virtual threads, and the filesystem. Preparation is
 * asynchronous - addTask returns before the commit is on disk - so readiness is awaited with Awaitility,
 * never Thread.sleep.
 *
 * @author Sergio Lissner
 * Date: 9/4/2026
 * Time: 4:20 AM
 */
@Execution(ExecutionMode.SAME_THREAD)
public class GitFunctionAssetPreparingIntegrationTest {

    private static final String FUNCTION_CODE = "fn-py-git:1.0";
    private static final String PATH_IN_REPO = "fn";
    private static final String FUNCTION_FILE = "run.py";

    private static Path root;
    private static String repoUrl;
    private static final List<String> shas = new ArrayList<>();

    private static final AtomicInteger SEQ = new AtomicInteger();

    private static Globals globals;
    private static DownloadGitFunctionService service;

    @BeforeAll
    public static void setUp() throws Exception {
        root = Files.createTempDirectory("mh-git-asset-it-");

        // --- a real origin repo with two commits, built by JGit ---
        final Path origin = root.resolve("origin");
        Files.createDirectories(origin);
        try (Git git = Git.init().setDirectory(origin.toFile()).setInitialBranch("main").call()) {
            // fetching a bare sha needs the server to allow it; GitHub and GitLab do, a fresh local repo doesn't
            final StoredConfig cfg = git.getRepository().getConfig();
            cfg.setBoolean("uploadpack", null, "allowAnySHA1InWant", true);
            cfg.save();

            for (int i = 1; i <= 2; i++) {
                final Path fnDir = origin.resolve(PATH_IN_REPO);
                Files.createDirectories(fnDir);
                Files.writeString(fnDir.resolve(FUNCTION_FILE), "print('revision " + i + "')\n");
                Files.writeString(origin.resolve("README.md"), "readme " + i + "\n");
                git.add().addFilepattern(".").call();
                final RevCommit c = git.commit().setMessage("commit " + i).setSign(false).call();
                shas.add(c.getName());
            }
        }
        // file:// rather than a bare path, so git uses the real transport instead of the local-clone shortcut
        repoUrl = "file://" + origin.toAbsolutePath();

    }

    /**
     * A fresh Processor per @Test. The origin repo is read-only and shared, but the cache is what is under
     * test - sharing it would make every assertion about a NOT-yet-prepared commit depend on which sibling
     * test happened to run first.
     */
    @BeforeEach
    public void setUpPerTest() throws Exception {
        globals = new Globals();
        globals.testing = false;
        globals.processor.enabled = true;
        globals.processorResourcesPath = root.resolve("processor-" + SEQ.incrementAndGet());
        Files.createDirectories(globals.processorResourcesPath);

        service = new DownloadGitFunctionService(globals);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        if (root!=null && Files.exists(root)) {
            org.apache.commons.io.file.PathUtils.deleteDirectory(root);
        }
    }

    private static DownloadGitFunctionTask taskAt(String sha) {
        return new DownloadGitFunctionTask(
            FUNCTION_CODE, new GitInfo(repoUrl, "main", sha, PATH_IN_REPO), FUNCTION_FILE, NORMAL);
    }

    /** the path prepareWithSourcingAsGit hands to the Processor as the file to launch */
    private static Path assetFileOf(String sha) {
        final Path commits = GitCommitCache.commitsDir(
            globals.processorResourcesPath.resolve("git").resolve(StrUtils.asCode(repoUrl)));
        return GitCommitCache.entryPath(commits, sha).resolve(PATH_IN_REPO).resolve(FUNCTION_FILE);
    }

    private static void awaitReady(DownloadGitFunctionTask task) {
        Awaitility.await()
            .atMost(Duration.ofSeconds(60))
            .pollInterval(Duration.ofMillis(200))
            .until(() -> service.isReady(task));
    }

    /** the same two calls TaskProcessor makes when it builds a git-sourced Task's asset dir */
    private static void copyAsTaskProcessorWould(String sha, Path taskAssetDir) throws IOException {
        final Path commits = GitCommitCache.commitsDir(
            globals.processorResourcesPath.resolve("git").resolve(StrUtils.asCode(repoUrl)));
        GitCommitCache.copyToTask(GitCommitCache.entryPath(commits, sha), PATH_IN_REPO, taskAssetDir);
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- the scenario

    @Test
    public void test_twoTasksOnTwoRevisionsOfOneRepoAreBothPrepared() throws Exception {
        final DownloadGitFunctionTask task1 = taskAt(shas.get(0));
        final DownloadGitFunctionTask task2 = taskAt(shas.get(1));

        // --- Task #1 arrives: not prepared, so not launchable yet ---
        assertFalse(service.isReady(task1), "nothing has been cloned yet");
        service.addTask(task1);
        awaitReady(task1);

        assertTrue(Files.exists(assetFileOf(shas.get(0))), "the file to launch must exist once ready");
        assertEquals("print('revision 1')\n", read(assetFileOf(shas.get(0))));

        // --- the repo moves on and Task #2 arrives pinned to the newer commit ---
        assertFalse(service.isReady(task2), "the newer revision has not been prepared by the older Task");
        service.addTask(task2);
        awaitReady(task2);

        assertEquals("print('revision 2')\n", read(assetFileOf(shas.get(1))));

        // --- and Task #1 is still runnable, on its own revision ---
        assertTrue(service.isReady(task1), "preparing a newer revision must not un-prepare an older one");
        assertEquals("print('revision 1')\n", read(assetFileOf(shas.get(0))),
            "this is what one shared working tree got wrong: checkout of 456 rewrote what 123 was running");

        // --- both revisions coexist as separate cache entries ---
        final Path commits = GitCommitCache.commitsDir(
            globals.processorResourcesPath.resolve("git").resolve(StrUtils.asCode(repoUrl)));
        final List<String> cached = GitCommitCache.cachedShas(commits);
        assertEquals(2, cached.size(), "expected one entry per revision, got: " + cached);
        assertTrue(cached.contains(shas.get(0)));
        assertTrue(cached.contains(shas.get(1)));
    }

    @Test
    public void test_oneObjectStoreIsSharedByBothRevisions() throws Exception {
        final DownloadGitFunctionTask task1 = taskAt(shas.get(0));
        final DownloadGitFunctionTask task2 = taskAt(shas.get(1));
        service.addTask(task1);
        service.addTask(task2);
        awaitReady(task1);
        awaitReady(task2);

        final Path repoRoot = globals.processorResourcesPath.resolve("git").resolve(StrUtils.asCode(repoUrl));
        assertTrue(Files.isDirectory(GitCommitCache.objectsDir(repoRoot)), "one object store per repo");
        assertTrue(Files.exists(GitCommitCache.objectsDir(repoRoot).resolve("HEAD")), "it must be a real repo");
        assertFalse(Files.exists(GitCommitCache.objectsDir(repoRoot).resolve(PATH_IN_REPO)),
            "the object store is bare - nothing is ever checked out into it");
    }

    @Test
    public void test_twoTasksOnDifferentRevisionsGetTheirOwnAssetDirs() throws Exception {
        final DownloadGitFunctionTask task1 = taskAt(shas.get(0));
        final DownloadGitFunctionTask task2 = taskAt(shas.get(1));
        service.addTask(task1);
        service.addTask(task2);
        awaitReady(task1);
        awaitReady(task2);

        // what TaskProcessor does when it builds each Task's environment, just before launching it
        final Path asset1 = root.resolve("task-home-A").resolve("asset");
        final Path asset2 = root.resolve("task-home-B").resolve("asset");
        copyAsTaskProcessorWould(shas.get(0), asset1);
        copyAsTaskProcessorWould(shas.get(1), asset2);

        assertEquals("print('revision 1')\n", read(asset1.resolve(FUNCTION_FILE)));
        assertEquals("print('revision 2')\n", read(asset2.resolve(FUNCTION_FILE)),
            "two Tasks of two ExecContexts must each run the revision they were pinned to");
    }

    @Test
    public void test_aReRunGetsAPristineCopyAfterTheFunctionDamagedTheLast() throws Exception {
        final DownloadGitFunctionTask task = taskAt(shas.get(0));
        service.addTask(task);
        awaitReady(task);

        final Path asset = root.resolve("task-home-rerun").resolve("asset");
        copyAsTaskProcessorWould(shas.get(0), asset);
        Files.writeString(asset.resolve(FUNCTION_FILE), "print('vandalised')\n");

        // cleaningPolicy=ASSETS removes the dir when the Task finishes; the next attempt copies again
        org.apache.commons.io.file.PathUtils.deleteDirectory(asset);
        copyAsTaskProcessorWould(shas.get(0), asset);

        assertEquals("print('revision 1')\n", read(asset.resolve(FUNCTION_FILE)),
            "a re-run must not inherit the damage the previous attempt did to its own copy");
    }

    @Test
    public void test_aTaskGetsItsOwnCopyThatItCanSafelyDamage() throws Exception {
        final DownloadGitFunctionTask task = taskAt(shas.get(0));
        service.addTask(task);
        awaitReady(task);

        final Path commits = GitCommitCache.commitsDir(
            globals.processorResourcesPath.resolve("git").resolve(StrUtils.asCode(repoUrl)));
        final Path entry = GitCommitCache.entryPath(commits, shas.get(0));

        final Path taskAsset = root.resolve("task-home-1").resolve("asset");
        GitCommitCache.copyToTask(entry, PATH_IN_REPO, taskAsset);
        assertEquals("print('revision 1')\n", read(taskAsset.resolve(FUNCTION_FILE)));

        // an external Function is free to rewrite the scripts it was handed - that is why it gets a copy
        Files.writeString(taskAsset.resolve(FUNCTION_FILE), "print('vandalised')\n");

        assertEquals("print('revision 1')\n", read(assetFileOf(shas.get(0))),
            "a Function damaged the cache entry, so every later Task would inherit the damage");
        assertTrue(service.isReady(task), "the Function is still reported ready after a Task damaged its own copy");
    }

    @Test
    public void test_anUnresolvedRevisionCannotEvenBecomeATask() {
        // The Dispatcher resolves HEAD to a sha when it creates the ExecContext, so every Task reaching a
        // Processor carries a sha. A Task carrying anything else is a broken invariant, not an input to
        // cope with, and it fails at construction rather than becoming work that can never complete.
        for (String unresolved : List.of("HEAD", "main", "v1.0", "")) {
            final IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new DownloadGitFunctionTask(
                    FUNCTION_CODE, new GitInfo(repoUrl, "main", unresolved, PATH_IN_REPO), FUNCTION_FILE, NORMAL),
                "'" + unresolved + "' isn't a revision and must not be accepted");
            assertTrue(e.getMessage().startsWith("816.600"), e.getMessage());
            assertTrue(e.getMessage().contains(FUNCTION_CODE), e.getMessage());
        }
    }

    @Test
    public void test_reRequestingAPreparedRevisionIsANoop() throws Exception {
        final DownloadGitFunctionTask task = taskAt(shas.get(1));
        service.addTask(task);
        awaitReady(task);

        final Path commits = GitCommitCache.commitsDir(
            globals.processorResourcesPath.resolve("git").resolve(StrUtils.asCode(repoUrl)));
        // a sentinel inside the entry survives only if the entry was not rebuilt
        final Path sentinel = GitCommitCache.entryPath(commits, shas.get(1)).resolve("sentinel.txt");
        Files.writeString(sentinel, "still here");

        service.addTask(task);
        awaitReady(task);

        assertTrue(Files.exists(sentinel), "an already-prepared revision must not be fetched or extracted again");
    }
}

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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the commit cache against a REAL git repo with REAL commits, built in a temp dir by JGit -
 * no git binary, no network. The extractor handed to the cache is a real JGit tree walk, so what these
 * tests drive is the actual materialization path, not a stand-in for it.
 *
 * @author Sergio Lissner
 * Date: 9/4/2026
 * Time: 12:40 AM
 */
@Execution(ExecutionMode.SAME_THREAD)
public class GitCommitCacheTest {

    private static Path root;
    private static Path repoDir;
    private static Git git;

    /** three commits, each rewriting the same two files, so content alone identifies the revision */
    private static final List<String> shas = new ArrayList<>();

    @BeforeAll
    public static void setUp() throws Exception {
        root = Files.createTempDirectory("mh-git-cache-test-");
        repoDir = root.resolve("origin");
        Files.createDirectories(repoDir);
        git = Git.init().setDirectory(repoDir.toFile()).setInitialBranch("main").call();

        for (int i = 1; i <= 3; i++) {
            final Path fnDir = repoDir.resolve("fn");
            Files.createDirectories(fnDir);
            Files.writeString(fnDir.resolve("run.py"), "print('revision " + i + "')\n");
            Files.writeString(fnDir.resolve("version.txt"), "v" + i + "\n");
            Files.writeString(repoDir.resolve("README.md"), "readme " + i + "\n");
            git.add().addFilepattern(".").call();
            final RevCommit c = git.commit().setMessage("commit " + i).setSign(false).call();
            shas.add(c.getName());
        }
    }

    @AfterAll
    public static void tearDown() throws Exception {
        if (git!=null) {
            git.close();
        }
        if (root!=null && Files.exists(root)) {
            org.apache.commons.io.file.PathUtils.deleteDirectory(root);
        }
    }

    /**
     * The real extractor: walk the tree of one commit and write every blob out. This is what production
     * would do with git; here JGit does it in-process so the test needs no external binary.
     */
    private static void extract(String sha, Path target) {
        try {
            final Repository repository = git.getRepository();
            try (RevWalk revWalk = new RevWalk(repository)) {
                final RevCommit commit = revWalk.parseCommit(ObjectId.fromString(sha));
                try (TreeWalk treeWalk = new TreeWalk(repository)) {
                    treeWalk.addTree(commit.getTree());
                    treeWalk.setRecursive(true);
                    while (treeWalk.next()) {
                        final Path out = target.resolve(treeWalk.getPathString());
                        Files.createDirectories(out.getParent());
                        final ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
                        try (OutputStream os = Files.newOutputStream(out)) {
                            loader.copyTo(os);
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            throw new RuntimeException("extraction of " + sha + " failed", e);
        }
    }

    private static Path freshCacheRoot(String name) throws IOException {
        final Path p = root.resolve("cache-" + name);
        Files.createDirectories(p);
        return p;
    }

    private static String readRunPy(Path entry) throws IOException {
        return Files.readString(entry.resolve("fn").resolve("run.py"), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ fixture

    @Test
    public void test_theFixtureProducesThreeDistinctCommits() {
        assertEquals(3, shas.size());
        assertEquals(3, Set.copyOf(shas).size(), "each commit must have its own sha");
        shas.forEach(sha -> assertTrue(GtiUtils.isSha(sha), sha));
    }

    // ------------------------------------------------------------------ one sha

    @Test
    public void test_materializesTheRequestedCommit() throws Exception {
        final Path cache = freshCacheRoot("single");
        final Path entry = GitCommitCache.get(cache, shas.get(0), t -> extract(shas.get(0), t));

        assertTrue(Files.isDirectory(entry));
        assertEquals(shas.get(0), entry.getFileName().toString());
        assertEquals("print('revision 1')\n", readRunPy(entry));
        assertEquals("v1\n", Files.readString(entry.resolve("fn").resolve("version.txt")));
    }

    @Test
    public void test_aSecondCallDoesNotReExtract() throws Exception {
        final Path cache = freshCacheRoot("reuse");
        final String sha = shas.get(1);
        final Path first = GitCommitCache.get(cache, sha, t -> extract(sha, t));

        // a sentinel inside the entry survives only if the entry was NOT rebuilt
        final Path sentinel = first.resolve("sentinel.txt");
        Files.writeString(sentinel, "still here");

        final Path second = GitCommitCache.get(cache, sha, t -> extract(sha, t));

        assertEquals(first, second);
        assertTrue(Files.exists(sentinel), "the entry was re-extracted when it should have been reused");
    }

    @Test
    public void test_noTmpDirIsLeftBehind() throws Exception {
        final Path cache = freshCacheRoot("no-tmp");
        GitCommitCache.get(cache, shas.get(0), t -> extract(shas.get(0), t));

        try (var stream = Files.list(cache)) {
            assertTrue(stream.noneMatch(p -> p.getFileName().toString().startsWith(".tmp-")));
        }
    }

    @Test
    public void test_aFailedExtractionLeavesNoEntryAndNoTmp() throws Exception {
        final Path cache = freshCacheRoot("failed");
        final String sha = shas.get(0);

        assertThrows(RuntimeException.class, () -> GitCommitCache.get(cache, sha, t -> {
            try {
                Files.writeString(t.resolve("half-written.txt"), "partial");
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
            throw new RuntimeException("boom");
        }));

        assertFalse(GitCommitCache.isCached(cache, sha), "a half-extracted commit must never become an entry");
        assertEquals(List.of(), GitCommitCache.cachedShas(cache));
        try (var stream = Files.list(cache)) {
            assertEquals(0, stream.count(), "the tmp dir must be cleaned up");
        }
    }

    @Test
    public void test_rejectsSomethingThatIsNotASha() throws Exception {
        final Path cache = freshCacheRoot("bad-sha");
        assertThrows(IllegalStateException.class, () -> GitCommitCache.get(cache, "HEAD", t -> {}));
        assertThrows(IllegalStateException.class, () -> GitCommitCache.get(cache, "main", t -> {}));
    }

    @Test
    public void test_isCachedAnswersForANonShaInsteadOfThrowing() throws Exception {
        final Path cache = freshCacheRoot("is-cached-bad-sha");
        // isCached sits on the Task-assignment path, where throwing would take out the caller
        assertFalse(GitCommitCache.isCached(cache, "HEAD"));
        assertFalse(GitCommitCache.isCached(cache, "main"));
        assertFalse(GitCommitCache.isCached(cache, null));
        assertFalse(GitCommitCache.isCached(cache, ""));
    }

    // ------------------------------------------------------------------ several shas

    @Test
    public void test_differentShasGetTheirOwnEntriesWithTheirOwnContent() throws Exception {
        final Path cache = freshCacheRoot("multi");
        for (String sha : shas) {
            GitCommitCache.get(cache, sha, t -> extract(sha, t));
        }

        assertEquals(3, GitCommitCache.cachedShas(cache).size());
        for (int i = 0; i < shas.size(); i++) {
            final Path entry = GitCommitCache.entryPath(cache, shas.get(i));
            assertEquals("print('revision " + (i + 1) + "')\n", readRunPy(entry),
                "entry " + shas.get(i) + " holds the content of another revision");
        }
    }

    @Test
    public void test_anOlderShaIsStillIntactAfterANewerOneIsMaterialized() throws Exception {
        final Path cache = freshCacheRoot("no-overwrite");
        final String older = shas.get(0);
        final String newer = shas.get(2);

        GitCommitCache.get(cache, older, t -> extract(older, t));
        GitCommitCache.get(cache, newer, t -> extract(newer, t));

        assertEquals("print('revision 1')\n", readRunPy(GitCommitCache.entryPath(cache, older)),
            "materializing a newer revision must not disturb an older one - this is what one shared working tree got wrong");
        assertEquals("print('revision 3')\n", readRunPy(GitCommitCache.entryPath(cache, newer)));
    }

    // ------------------------------------------------------------------ parallel

    @Test
    public void test_parallelMaterializationOfDifferentShas() throws Exception {
        final Path cache = freshCacheRoot("par-diff");
        final int threadsPerSha = 4;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(shas.size() * threadsPerSha);
        final List<Thread> threads = new ArrayList<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        for (String sha : shas) {
            for (int i = 0; i < threadsPerSha; i++) {
                final Thread t = new Thread(() -> {
                    try {
                        start.await();
                        final Path entry = GitCommitCache.get(cache, sha, dir -> extract(sha, dir));
                        assertEquals(sha, entry.getFileName().toString());
                    }
                    catch (Throwable th) {
                        failure.compareAndSet(null, th);
                    }
                    finally {
                        done.countDown();
                    }
                });
                threads.add(t);
                t.start();
            }
        }
        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "threads didn't finish");
        for (Thread t : threads) {
            t.join();
        }
        assertNull(failure.get(), () -> "a thread failed: " + failure.get());

        assertEquals(3, GitCommitCache.cachedShas(cache).size(), "exactly one entry per sha");
        for (int i = 0; i < shas.size(); i++) {
            assertEquals("print('revision " + (i + 1) + "')\n", readRunPy(GitCommitCache.entryPath(cache, shas.get(i))));
        }
        try (var stream = Files.list(cache)) {
            assertEquals(3, stream.count(), "no tmp dirs may survive a parallel run");
        }
    }

    @Test
    public void test_parallelMaterializationOfTheSameShaProducesOneEntry() throws Exception {
        final Path cache = freshCacheRoot("par-same");
        final String sha = shas.get(1);
        final int threads = 12;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        final Set<String> seenPaths = ConcurrentHashMap.newKeySet();
        final AtomicInteger extractions = new AtomicInteger();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final List<Thread> ts = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final Thread t = new Thread(() -> {
                try {
                    start.await();
                    final Path entry = GitCommitCache.get(cache, sha, dir -> {
                        extractions.incrementAndGet();
                        extract(sha, dir);
                    });
                    seenPaths.add(entry.toAbsolutePath().toString());
                }
                catch (Throwable th) {
                    failure.compareAndSet(null, th);
                }
                finally {
                    done.countDown();
                }
            });
            ts.add(t);
            t.start();
        }
        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS));
        for (Thread t : ts) {
            t.join();
        }

        assertNull(failure.get(), () -> "a racing thread failed: " + failure.get());
        assertEquals(1, seenPaths.size(), "every thread must end up on the same entry");
        assertEquals(List.of(sha), GitCommitCache.cachedShas(cache));
        assertEquals("print('revision 2')\n", readRunPy(GitCommitCache.entryPath(cache, sha)));
        assertTrue(extractions.get() <= threads,
            "racing extractions are allowed and wasteful, but only one may become the entry");
        try (var stream = Files.list(cache)) {
            assertEquals(1, stream.count(), "the losing threads must clean up their tmp dirs");
        }
    }

    // ------------------------------------------------------------------ the copy the Task gets

    @Test
    public void test_copyToTaskTakesOnlyTheSubdirNamedByGitInfoPath() throws Exception {
        final Path cache = freshCacheRoot("copy-path");
        final String sha = shas.get(2);
        final Path entry = GitCommitCache.get(cache, sha, t -> extract(sha, t));

        final Path taskAsset = root.resolve("task-1").resolve("asset");
        GitCommitCache.copyToTask(entry, "fn", taskAsset);

        assertTrue(Files.exists(taskAsset.resolve("run.py")));
        assertTrue(Files.exists(taskAsset.resolve("version.txt")));
        assertFalse(Files.exists(taskAsset.resolve("README.md")), "only GitInfo.path should be copied");
    }

    @Test
    public void test_copyToTaskTakesTheWholeTreeWhenPathIsBlank() throws Exception {
        final Path cache = freshCacheRoot("copy-all");
        final String sha = shas.get(0);
        final Path entry = GitCommitCache.get(cache, sha, t -> extract(sha, t));

        final Path taskAsset = root.resolve("task-2").resolve("asset");
        GitCommitCache.copyToTask(entry, null, taskAsset);

        assertTrue(Files.exists(taskAsset.resolve("README.md")));
        assertTrue(Files.exists(taskAsset.resolve("fn").resolve("run.py")));
    }

    @Test
    public void test_aFunctionRewritingItsOwnScriptsCannotDamageTheCache() throws Exception {
        final Path cache = freshCacheRoot("mutation");
        final String sha = shas.get(0);
        final Path entry = GitCommitCache.get(cache, sha, t -> extract(sha, t));

        final Path taskA = root.resolve("task-a").resolve("asset");
        GitCommitCache.copyToTask(entry, "fn", taskA);

        // the whole reason a Task gets a copy: an external Function may rewrite what it was handed
        Files.writeString(taskA.resolve("run.py"), "print('vandalised')\n");
        Files.delete(taskA.resolve("version.txt"));

        assertEquals("print('revision 1')\n", readRunPy(entry), "the cache entry was damaged by a Task");

        final Path taskB = root.resolve("task-b").resolve("asset");
        GitCommitCache.copyToTask(entry, "fn", taskB);
        assertEquals("print('revision 1')\n", Files.readString(taskB.resolve("run.py")),
            "a later Task must still get pristine content");
        assertTrue(Files.exists(taskB.resolve("version.txt")));
    }

    @Test
    public void test_copyToTaskFailsLoudlyForAMissingPath() throws Exception {
        final Path cache = freshCacheRoot("copy-missing");
        final String sha = shas.get(0);
        final Path entry = GitCommitCache.get(cache, sha, t -> extract(sha, t));

        final IOException e = assertThrows(IOException.class,
            () -> GitCommitCache.copyToTask(entry, "no-such-dir", root.resolve("task-3").resolve("asset")));
        assertTrue(e.getMessage().startsWith("01.923.040"), e.getMessage());
    }

    // ------------------------------------------------------------------ janitor

    @Test
    public void test_sweepRemovesAbandonedTmpDirsAndKeepsEntries() throws Exception {
        final Path cache = freshCacheRoot("sweep");
        final String sha = shas.get(0);
        GitCommitCache.get(cache, sha, t -> extract(sha, t));

        // what a crash mid-extraction leaves behind
        final Path abandoned = cache.resolve(".tmp-" + java.util.UUID.randomUUID());
        Files.createDirectories(abandoned);
        Files.writeString(abandoned.resolve("partial.txt"), "x");

        assertEquals(List.of(sha), GitCommitCache.cachedShas(cache),
            "an abandoned tmp dir must never be reported as a cached commit");

        assertEquals(1, GitCommitCache.sweepAbandoned(cache));
        assertFalse(Files.exists(abandoned));
        assertTrue(GitCommitCache.isCached(cache, sha), "the sweep must not touch real entries");
    }
}

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
import ai.metaheuristic.ai.functions.FunctionEnums.DownloadPriority;
import ai.metaheuristic.api.data.GitData;
import ai.metaheuristic.commons.utils.GitCommitCache;
import ai.metaheuristic.commons.utils.GtiUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.utils.threads.MultiTenantedQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static ai.metaheuristic.ai.functions.FunctionRepositoryData.DownloadGitFunctionTask;

/**
 * Makes a git-sourced Function's payload present on this Processor, at the exact revision the Task is
 * pinned to.
 *
 * <p>The sibling {@link DownloadFunctionService} is a separate implementation on purpose - the two get
 * their bytes from different places and should be free to change independently. What they do NOT share
 * is any of the asset-manager machinery: there is no chunked HTTP transfer here, no chunkSize, no
 * checksum/signature verification, and no round-trip to fetch a Function config, because a git revision
 * is self-identifying and the Dispatcher already sent everything else in TaskParamsYaml.
 *
 * <p>The work is exactly two steps, and both are idempotent:
 * <ol>
 *   <li>make sure the bare object store holds the commit - fetch only if it doesn't;</li>
 *   <li>materialize the commit into {@code commits/&lt;sha&gt;/} - only if it isn't there.</li>
 * </ol>
 *
 * <p>Readiness is then the existence of that directory. There is no in-memory state to go stale: the
 * atomic rename in {@link GitCommitCache} means a directory that exists is a commit that was fully
 * materialized.
 *
 * <p>Error code prefix: {@code 01.817.} (unique to this class).
 *
 * @author Sergio Lissner
 */
@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class DownloadGitFunctionService {

    private static final GitData.GitContext GIT_CONTEXT = new GitData.GitContext(600L, 1000);

    private final Globals globals;

    // tenant == normalized repo url, so one virtual thread per repo. Writes into a repo's object store
    // are serialised by construction, and different repos make progress independently.
    // checkForDouble collapses identical pending requests - same function, same repo, SAME sha.
    private final MultiTenantedQueue<String, DownloadGitFunctionTask> downloadFunctionQueue =
        new MultiTenantedQueue<>(100, Duration.ZERO, true, "git-fetch-", this::prepareCommit);

    public void addTask(DownloadGitFunctionTask task) {
        downloadFunctionQueue.putToQueue(task);
    }

    @Async
    @EventListener
    public void processAssetPreparing(DownloadGitFunctionTask event) {
        try {
            addTask(event);
        } catch (Throwable th) {
            log.error("01.817.010 Error, need to investigate ", th);
        }
    }

    /** Root of one repo's cache: {@code <processorResources>/git/<repoCode>/} */
    public Path repoRoot(String repoUrl) {
        return globals.processorResourcesPath.resolve("git").resolve(StrUtils.asCode(repoUrl));
    }

    /** Readiness is a filesystem fact: the pinned commit is materialized on this Processor, or it isn't. */
    public boolean isReady(DownloadGitFunctionTask task) {
        return GitCommitCache.isCached(GitCommitCache.commitsDir(repoRoot(task.git.repo)), task.git.commit);
    }

    public void prepareCommit(DownloadGitFunctionTask task) {
        if (globals.testing || !globals.processor.enabled) {
            return;
        }
        // the revision is a sha - DownloadGitFunctionTask refuses to be constructed otherwise
        final String sha = task.git.commit;

        final Path root = repoRoot(task.git.repo);
        final Path commits = GitCommitCache.commitsDir(root);
        if (GitCommitCache.isCached(commits, sha)) {
            return;
        }

        try {
            final Path objects = GitCommitCache.objectsDir(root);
            GtiUtils.ensureBareRepo(objects, task.git.repo, GIT_CONTEXT);

            if (!GtiUtils.hasCommit(objects, sha, GIT_CONTEXT)) {
                final var result = GtiUtils.fetchCommit(objects, sha, GIT_CONTEXT);
                if (!result.ok) {
                    log.error("01.817.030 Can't fetch {} from {}, error: {}", sha, task.git.repo, result.error);
                    return;
                }
                if (!GtiUtils.hasCommit(objects, sha, GIT_CONTEXT)) {
                    log.error("01.817.040 {} still isn't present after fetching it from {}. The server may not allow "
                        + "fetching by object id, or the commit isn't reachable from any ref", sha, task.git.repo);
                    return;
                }
            }

            GitCommitCache.get(commits, sha, dir -> materialize(objects, sha, dir));
            log.info("01.817.050 commit {} of {} is ready for function {}", sha, task.git.repo, task.functionCode);
        }
        catch (Throwable th) {
            log.error("01.817.060 Can't prepare commit " + sha + " of " + task.git.repo, th);
        }
    }

    /** git archive reads the commit's tree; nothing is checked out, so other revisions are undisturbed. */
    private static void materialize(Path objects, String sha, Path target) {
        try {
            final Path tar = target.getParent().resolve(target.getFileName() + ".tar");
            try {
                final var result = GtiUtils.archiveCommit(objects, sha, tar, GIT_CONTEXT);
                if (!result.ok) {
                    throw new IOException("01.817.070 git archive failed for " + sha + ", error: " + result.error);
                }
                GitCommitCache.untar(tar, target);
            }
            finally {
                Files.deleteIfExists(tar);
            }
        }
        catch (IOException e) {
            throw new RuntimeException("01.817.080 Can't materialize " + sha, e);
        }
    }
}

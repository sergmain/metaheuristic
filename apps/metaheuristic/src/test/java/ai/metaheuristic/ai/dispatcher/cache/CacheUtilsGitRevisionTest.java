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

package ai.metaheuristic.ai.dispatcher.cache;

import ai.metaheuristic.ai.dispatcher.data.CacheData;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParams;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.utils.ExecContextGitSourceUtils;
import ai.metaheuristic.commons.utils.TaskParamsUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the dispatcher's cache key says about a git-sourced Function whose descriptor declares {@code commit: HEAD}.
 *
 * <p>The descriptor names HEAD. Each ExecContext resolves HEAD to a sha once, when it is created, and pins that sha
 * into every Task it produces ({@link ExecContextGitSourceUtils#resolveGitSources},
 * {@link ExecContextGitSourceUtils#pinGitRevision}). A push between two ExecContexts therefore gives their Tasks
 * different code - and a Task whose Function is a different revision has never been run, whatever its inputs are.
 * The cache is looked up by {@link CacheUtils#getKey} alone ({@code TaskCheckCachingService}), so the key is where
 * that has to show.
 *
 * <p>Internal git, as the MH tests do it: a real repository built in a temp dir by JGit, real commits, real shas -
 * no git binary and no network. HEAD is resolved against that repository, the question production asks the remote
 * with ls-remote.
 *
 * <p>No doubles: the Task is built by the production {@link TaskParamsUtils#toFunctionConfig} and pinned by the
 * production pin, and the key is computed by the production {@link CacheUtils#getKey}. The variable store the key
 * reads is one fixed in-memory store, handed in through the same function parameters production fills with its
 * services; a lookup the Task never makes fails loudly instead of answering.
 *
 * @author Sergio Lissner
 */
@Execution(ExecutionMode.CONCURRENT)
public class CacheUtilsGitRevisionTest {

    private static final String CODE = "fn-git-cached:1.0";
    private static final String BRANCH = "master";
    private static final String PATH_IN_REPO = "fn";

    /** The Task's one input: the same bytes in every run, so only the Function's revision can differ. */
    private static final long INPUT_VARIABLE_ID = 11L;
    private static final long INPUT_BLOB_ID = 101L;
    private static final byte[] INPUT = "the same input in every run".getBytes(StandardCharsets.UTF_8);

    @Test
    public void test_aPushToTheFunctionsRepoChangesTheCacheKey(@TempDir Path tmp) throws Exception {
        try (Git origin = originRepo(tmp)) {
            final String sha1 = commitScript(origin, "print('v1')\n");
            final CacheData.FullKey beforePush = keyOfATaskOfANewExecContext(origin);

            final String sha2 = commitScript(origin, "print('v2')\n");
            final CacheData.FullKey afterPush = keyOfATaskOfANewExecContext(origin);

            assertNotEquals(sha1, sha2, "the push must have moved the branch tip, or this test proves nothing");
            assertNotEquals(beforePush.asString(), afterPush.asString(),
                    "a Task pinned to another revision runs other code - it must miss the cache, not be answered from it");
        }
    }

    @Test
    public void test_withoutAPushEveryExecContextStillSharesTheCacheEntry(@TempDir Path tmp) throws Exception {
        try (Git origin = originRepo(tmp)) {
            commitScript(origin, "print('v1')\n");

            final CacheData.FullKey first = keyOfATaskOfANewExecContext(origin);
            final CacheData.FullKey second = keyOfATaskOfANewExecContext(origin);

            assertEquals(first.asString(), second.asString(),
                    "the same revision is the same code: a second run with the same input must still be answered from the cache");
        }
    }

    @Test
    public void test_theKeyCarriesTheShaTheExecContextPinnedNotHead(@TempDir Path tmp) throws Exception {
        try (Git origin = originRepo(tmp)) {
            final String sha = commitScript(origin, "print('v1')\n");

            final CacheData.FullKey key = keyOfATaskOfANewExecContext(origin);

            assertEquals(repoUrl(origin) + '@' + sha + ':' + PATH_IN_REPO, key.gitRevision,
                    "HEAD names no revision - only the sha the ExecContext resolved separates one push from the next");
        }
    }

    @Test
    public void test_theKeyOfAFunctionThatIsNotGitSourcedIsExactlyWhatItWasBefore() {
        final FunctionConfigYaml descriptor = new FunctionConfigYaml();
        descriptor.function.code = "fn-dispatcher:1.0";
        descriptor.function.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        final TaskParamsYaml tpy = new TaskParamsYaml();
        tpy.task.function = TaskParamsUtils.toFunctionConfig(descriptor);

        final CacheData.FullKey key = keyOf(tpy);

        assertNull(key.gitRevision);
        // the key exactly as it was written before the revision was part of it: every entry cached for a Function
        // that is not git-sourced stays reachable
        assertEquals("{\"functionCode\":\"fn-dispatcher:1.0\",\"funcParams\":\"\",\"inline\":{},\"inputs\":[{\"sha256\":"
                        + "\"e71551d5f82a45bd4cab86be87ef16e5c8a10517416ff16d56a290b6b1ba417a\",\"length\":27}],\"metas\":[]}",
                key.asString());
    }

    // ---------- the world: a payload repo, and what an ExecContext does with it ----------

    /** The Function's payload repo - a real repository on its default branch, nothing committed yet. */
    private static Git originRepo(Path tmp) throws Exception {
        return Git.init().setDirectory(tmp.resolve("origin").toFile()).setInitialBranch(BRANCH).call();
    }

    /** A push of a new version of the Function's script. Returns the sha of the commit. */
    private static String commitScript(Git git, String script) throws Exception {
        final Path dir = git.getRepository().getWorkTree().toPath().resolve(PATH_IN_REPO);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("run.py"), script, StandardCharsets.UTF_8);
        git.add().addFilepattern(".").call();
        return git.commit().setMessage(script.strip()).setSign(false).call().getName();
    }

    private static String repoUrl(Git git) {
        return git.getRepository().getWorkTree().toPath().toUri().toString();
    }

    /** What ls-remote answers for the branch: its tip in the real repository, right now. */
    private static String branchTip(Git git) {
        try {
            final ObjectId tip = git.getRepository().resolve("refs/heads/" + BRANCH);
            assertNotNull(tip, "the branch has no commit yet");
            return tip.getName();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static FunctionConfigYaml gitDescriptorAtHead(Git origin) {
        final FunctionConfigYaml descriptor = new FunctionConfigYaml();
        descriptor.function.code = CODE;
        descriptor.function.sourcing = EnumsApi.FunctionSourcing.git;
        descriptor.function.git = new GitInfo(repoUrl(origin), BRANCH, "HEAD", PATH_IN_REPO);
        return descriptor;
    }

    /**
     * The cache key of a Task of an ExecContext created now: HEAD resolved once against the repo, the Function
     * copied out of its descriptor and pinned to that revision, as ExecContext creation and Task production do.
     */
    private static CacheData.FullKey keyOfATaskOfANewExecContext(Git origin) {
        final FunctionConfigYaml descriptor = gitDescriptorAtHead(origin);
        final ExecContextParams.GitSources pinned = ExecContextGitSourceUtils.resolveGitSources(
                List.of(CODE), code -> descriptor, git -> branchTip(origin));

        final TaskParamsYaml tpy = new TaskParamsYaml();
        tpy.task.function = TaskParamsUtils.toFunctionConfig(descriptor);
        ExecContextGitSourceUtils.pinGitRevision(tpy.task.function, pinned);
        return keyOf(tpy);
    }

    /** The production key, over a Task with the one input and the cache on, reading the one fixed store. */
    private static CacheData.FullKey keyOf(TaskParamsYaml tpy) {
        final TaskParamsYaml.InputVariable input = new TaskParamsYaml.InputVariable();
        input.id = INPUT_VARIABLE_ID;
        input.context = EnumsApi.VariableContext.local;
        input.name = "prompt";
        tpy.task.inputs.add(input);
        tpy.task.cache = new TaskParamsYaml.Cache(true, false, false);

        return CacheUtils.getKey(tpy, null,
                variableId -> variableId == INPUT_VARIABLE_ID ? INPUT_BLOB_ID : null,
                blobId -> { throw new IllegalStateException("this Task has no array input, blob #" + blobId); },
                blobId -> {
                    if (blobId != INPUT_BLOB_ID) {
                        throw new IllegalStateException("no such blob #" + blobId);
                    }
                    return new ByteArrayInputStream(INPUT);
                },
                variableId -> { throw new IllegalStateException("this Task has no global input, variable #" + variableId); });
    }
}

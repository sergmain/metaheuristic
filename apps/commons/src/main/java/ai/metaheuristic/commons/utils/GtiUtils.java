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

package ai.metaheuristic.commons.utils;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.AssetFile;
import ai.metaheuristic.api.data.GitData;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.file.PathUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static ai.metaheuristic.commons.system.SystemProcessLauncher.ExecResult;
import static ai.metaheuristic.commons.system.SystemProcessLauncher.execCmd;

/**
 * @author Sergio Lissner
 * Date: 11/24/2023
 * Time: 11:36 PM
 */
@Slf4j
public class GtiUtils {
    private static final List<String> GIT_VERSION_CMD = List.of("git", "--version");
    private static final String GIT_VERSION_PREFIX = "git version";
    private static final String GIT_PREFIX = "git";
    public static int taskConsoleOutputMaxLines = 1000;

    /**
     * Whether this repo can be cloned with --depth 1.
     *
     * <p>A clone here is always followed by a checkout of {@link GitInfo#commit}, and --depth 1 leaves
     * exactly one commit in the local repo. Any older revision is then simply not present and the
     * checkout fails with "reference is not a tree". HEAD - and a commit that isn't set at all, which
     * cannot address a revision either way - is the only case where the tip of the branch IS the
     * revision wanted, so it is the only case where the shallow clone is correct.
     *
     * <p>A branch is required as well because --depth 1 alone clones the remote's default branch; a
     * subsequent 'pull origin &lt;other-branch&gt;' into that shallow repo merges two histories with no
     * common ancestor and is refused. --depth and --branch travel together.
     */
    public static boolean isShallowCloneSafe(@Nullable String branch, @Nullable String commit) {
        if (StringUtils.isBlank(branch)) {
            return false;
        }
        return isHeadRevision(commit);
    }

    /**
     * Whether the descriptor names a moving target rather than a revision.
     *
     * <p>HEAD is a POLICY - "whatever is on the tip of the branch when you look" - so it must be
     * resolved to a concrete sha exactly once, by the Dispatcher, when an ExecContext is created. An
     * unset commit cannot address a revision either, so it means the same thing here.
     */
    public static boolean isHeadRevision(@Nullable String commit) {
        return StringUtils.isBlank(commit) || "HEAD".equals(commit.strip());
    }

    /**
     * The only two revisions a git-sourced Function may declare: a full sha, or HEAD.
     *
     * <p>❗ A TAG IS NOT SUPPORTED, and this is deliberate rather than unimplemented. HEAD is a policy the
     * Dispatcher resolves to a sha once, when it creates an ExecContext, and a sha is already the answer.
     * A tag is neither: it is a name that resolves to a commit, and unlike a branch tip it carries an
     * implied promise of immutability that git does not actually keep - a tag can be moved or deleted, and
     * a moved tag would silently change what an already-running ExecContext believes it pinned. Adding tag
     * support would mean resolving it exactly like HEAD, which HEAD already covers, in exchange for a name
     * that can lie about being stable.
     *
     * <p>The same rules out a branch name, a short sha, and {@code HEAD~2}: everything that is not already
     * a revision has to be resolved, and there is exactly one supported way to say "resolve this for me".
     */
    public static boolean isSupportedRevision(@Nullable String commit) {
        return isHeadRevision(commit) || isSha(commit==null ? null : commit.strip());
    }

    /**
     * ❗ A case variant of HEAD gets its own message rather than the general one. It is the single most
     * likely thing an author writes by accident, and it is NOT a spelling git forgives: pseudo-refs are
     * matched exactly, so `git rev-parse head` fails outright - and if the repo happens to contain a
     * branch literally named `head`, it resolves to THAT instead, silently and to a different commit. So
     * the fix has to be stated, not left to be inferred from a list of what is allowed.
     */
    public static String unsupportedRevisionMessage(@Nullable String commit) {
        final String stripped = commit==null ? "" : commit.strip();
        if (!"HEAD".equals(stripped) && "HEAD".equalsIgnoreCase(stripped)) {
            return "a git-sourced function's commit was '" + commit + "', which is not HEAD. Git matches HEAD "
                + "case-sensitively, and '" + stripped + "' is a valid branch name that can point at a different "
                + "commit entirely. Change it to HEAD in upper case to always take the tip of the branch, or "
                + "replace it with a full 40-char sha to pin a revision.";
        }
        return "a git-sourced function's commit must be a full 40-char sha or 'HEAD', but was '" + commit
            + "'. Tags and branch names are not supported - use HEAD to always take the tip of the branch, "
            + "or a sha to pin a revision.";
    }

    /**
     * Clones the repo's DEFAULT branch, shallow, for bundle delivery.
     *
     * <p>No branch and no revision are given because delivery has neither to give: it always takes the
     * current state of the descriptors. Remote HEAD is a symref to whatever branch the repo was created
     * with - master for metaheuristic-assets, main elsewhere - so asking for the default is the only
     * spelling that works everywhere, and it is what a branch-less clone does.
     *
     * <p>--depth 1 is unconditionally correct here, unlike the Function payload path: there is exactly one
     * commit of interest, nothing is checked out afterwards, and no second branch is ever pulled in.
     */
    public static ExecResult cloneDefaultBranchShallow(Path repoDir, String gitUrl, GitData.GitContext gitContext) {
        return execClone(repoDir, gitUrl, gitContext, null, true);
    }

    public static List<String> lsRemoteCmd(String repo, String branch) {
        // git ls-remote <git-repo-url> refs/heads/<branch>
        return List.of("git", "ls-remote", repo, "refs/heads/" + branch);
    }

    /**
     * Pulls the sha out of `git ls-remote` output, whose lines are `<sha>\t<ref>`.
     *
     * <p>Returns null rather than throwing when nothing usable is there: an unknown branch produces
     * empty output and is a configuration error the caller reports with its own error code and its own
     * context, which is more useful than an exception from a parser.
     */
    @Nullable
    public static String parseLsRemoteOutput(@Nullable String console) {
        if (console==null) {
            return null;
        }
        for (String line : console.split("\\R")) {
            final String stripped = line.strip();
            if (stripped.isEmpty()) {
                continue;
            }
            final String sha = stripped.split("\\s+")[0];
            if (isSha(sha)) {
                return sha;
            }
        }
        return null;
    }

    public static boolean isSha(@Nullable String s) {
        if (s==null || s.length()!=40) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            final char ch = s.charAt(i);
            final boolean hex = (ch>='0' && ch<='9') || (ch>='a' && ch<='f') || (ch>='A' && ch<='F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    /**
     * Resolves the tip of a branch to a concrete sha WITHOUT cloning - `ls-remote` is one round-trip
     * over the wire and touches no working tree.
     *
     * @return the sha, or null when the repo or branch couldn't be reached or produced nothing usable
     */
    @Nullable
    public static String resolveHeadCommit(String repo, String branch, GitData.GitContext gitContext) {
        final List<String> cmd = lsRemoteCmd(repo, branch);
        log.info("exec {}", cmd);
        final ExecResult result = execGitCmd(cmd, gitContext);
        if (!result.ok || result.systemExecResult==null || !result.systemExecResult.isOk()) {
            log.warn("028.200 Error of resolving HEAD for repo: {}, branch: {}, error: {}", repo, branch, result.error);
            return null;
        }
        final String sha = parseLsRemoteOutput(result.systemExecResult.console);
        if (sha==null) {
            log.warn("028.210 Can't resolve HEAD for repo: {}, branch: {}, console: {}", repo, branch, result.systemExecResult.console);
        }
        return sha;
    }

    public static List<String> cloneCmd(Path repoDir, String gitUrl, @Nullable String branch, boolean shallow) {
        // git -C <path> clone <git-repo-url> git-repo
        // git -C <path> clone --depth 1 --branch <branch> <git-repo-url> git-repo
        final List<String> cmd = new ArrayList<>(List.of("git", "-C", repoDir.toAbsolutePath().toString(), "clone"));
        if (shallow) {
            cmd.addAll(List.of("--depth", "1"));
            if (!StringUtils.isBlank(branch)) {
                cmd.addAll(List.of("--branch", branch));
            }
        }
        cmd.addAll(List.of(gitUrl, CommonConsts.GIT_REPO));
        return List.copyOf(cmd);
    }

    public static ExecResult execClone(Path repoDir, String gitUrl, GitData.GitContext gitContext, @Nullable String branch, boolean shallow) {
        List<String> cmd = cloneCmd(repoDir, gitUrl, branch, shallow);
        log.info("exec {}", cmd);
        ExecResult result = execGitCmd(cmd, gitContext.withTimeout(0L));
        return result;
    }

    @SneakyThrows
    @Nullable
    public static ExecResult initGitRepository(GitInfo gitInfo, Path gitDir, String gitUrl, GitData.GitContext gitContext, boolean firstRun) {

        Path repoDir = gitDir.resolve(CommonConsts.GIT_REPO);
        log.info("028.070 Target dir: {}, exist: {}", repoDir.toAbsolutePath(), Files.exists(repoDir) );

        final boolean shallow = isShallowCloneSafe(gitInfo.branch, gitInfo.commit);

        if (Files.notExists(repoDir)) {
            ExecResult result = execClone(gitDir, gitUrl,  gitContext, gitInfo.branch, shallow);
            log.info("028.080 Result of cloning repo: {}", result.toString());
            if (!result.ok || !result.systemExecResult.isOk()) {
                result = tryToRepairRepo(gitDir, gitContext, gitUrl, gitInfo.branch, shallow);
                log.info("028.090 Result of repairing of repo: {}", result.toString());
                if (!result.ok || !result.systemExecResult.isOk()) {
                    return result;
                }
            }
        }
        ExecResult result = execRevParse(repoDir);
        log.info("028.100 Result of execRevParse: {}", result.toString());
        if (!result.ok) {
            return result;
        }
        if (!result.systemExecResult.isOk) {
            return new ExecResult(null, false, result.systemExecResult.console);
        }
        if (!"true".equals(result.systemExecResult.console.strip())) {
            result = tryToRepairRepo(repoDir, gitContext, gitUrl, gitInfo.branch, shallow);
            log.info("028.110 Result of tryToRepairRepo: {}", result.toString());
            if (!result.ok) {
                return result;
            }
            if (!result.systemExecResult.isOk) {
                return new ExecResult(null, false, result.systemExecResult.console);
            }
        }

        result = execResetHardHead(repoDir);
        log.info("028.120 Result of execResetHardHead: {}", result.toString());
        if (!result.ok) {
            return result;
        }
        if (!result.systemExecResult.isOk) {
            return new ExecResult(null, false, result.systemExecResult.console);
        }

        result = execCleanDF(repoDir);
        log.info("028.130 Result of execCleanDF: {}", result.toString());
        if (!result.ok) {
            return result;
        }
        if (!result.systemExecResult.isOk) {
            return new ExecResult(null, false, result.systemExecResult.console);
        }

        result = execPullOrigin(repoDir, gitInfo.branch);
        log.info("028.140 Result of execPullOrigin: {}", result.toString());
        if (!result.ok) {
            if (firstRun) {
                PathUtils.deleteDirectory(repoDir);
                return initGitRepository(gitInfo, gitDir, gitUrl, gitContext, false);
            }
            else {
                return result;
            }
        }
        if (!result.systemExecResult.isOk) {
            return new ExecResult(null, false, result.systemExecResult.console);
        }

        result = execCheckoutRevision(repoDir, gitInfo.commit);
        log.info("028.150 Result of execCheckoutRevision: {}", result.toString());
        if (!result.ok) {
            return result;
        }
        if (!result.systemExecResult.isOk) {
            return new ExecResult(null, false, result.systemExecResult.console);
        }
        log.info("028.160 repoDir: {}, exist: {}", repoDir.toAbsolutePath(), Files.exists(repoDir));
        return null;
    }

    public static ExecResult tryToRepairRepo(Path gitDir, GitData.GitContext gitContext, String gitUrl, @Nullable String branch, boolean shallow) {
        Path repoDir = gitDir.resolve(CommonConsts.GIT_REPO);
        ExecResult result;
        try {
            PathUtils.deleteDirectory(repoDir);
        }
        catch (IOException e) {
            //
        }
        if (Files.exists(repoDir)) {
            return new ExecResult(null,
                false,
                "028.170 Error preparing git repo " + repoDir.toAbsolutePath());
        }
        result = execClone(gitDir, gitUrl, gitContext, branch, shallow);
        return result;
    }

    // !!! DO NOT CHANGE THIS CLASS !!!
    // If you need to, then copy it to ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml
    // before any changing
    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(of={"status", "version", "error"})
    public static class GitStatusInfo {
        public EnumsApi.GitStatus status;
        public String version;
        public String error;

        public GitStatusInfo(EnumsApi.GitStatus status) {
            this.status = status;
        }
    }

    public static GitStatusInfo getGitStatus() {
        ExecResult result = execGitCmd(GIT_VERSION_CMD, 30L);
        if (!result.ok) {
            log.warn("028.010 Error of getting git status");
            log.warn("\tresult.ok: {}",  result.ok);
            log.warn("\tresult.error: {}",  result.error);
            log.warn("\tresult.functionDir: {}",  result.functionDir !=null ? result.functionDir.toAbsolutePath() : null);
            log.warn("\tresult.systemExecResult: {}",  result.systemExecResult);
            return new GitStatusInfo(EnumsApi.GitStatus.error, null, "028.010 Error: " + result.error);
        }

        // at this point result.systemExecResult must be not null, it can be null only if result.ok==false, but see above
        if (result.systemExecResult.exitCode!=0) {
            return new GitStatusInfo(
                    EnumsApi.GitStatus.not_found, null,
                    "028.013 Console: " + result.systemExecResult.console);
        }
        return new GitStatusInfo(EnumsApi.GitStatus.installed, getGitVersion(result.systemExecResult.console.toLowerCase()), null);
    }

    public static ExecResult execGitCmd(List<String> gitVersionCmd, GitData.GitContext gitContext) {
        return execCmd(gitVersionCmd, gitContext.timeout(), gitContext.consoleOutputMaxLines());
    }

    public static ExecResult execGitCmd(List<String> gitVersionCmd, long timeout) {
        return execCmd(gitVersionCmd, timeout, taskConsoleOutputMaxLines);
    }

    public static AssetFile prepareFunctionDir(final Path resourceDir, String functionCode) {
        final AssetFile assetFile = new AssetFile();
        final Path trgDir = ArtifactCommonUtils.prepareFunctionPath(resourceDir);
        log.info("Target dir: {}, exist: {}", trgDir.toAbsolutePath(), Files.exists(trgDir));
        if (Files.notExists(trgDir)) {
            try {
                Files.createDirectories(trgDir);
            }
            catch (IOException e) {
                assetFile.isError = true;
                log.error("028.030 Can't create function dir: {}", trgDir.toAbsolutePath());
                return assetFile;
            }
        }
        final String resId = ArtifactCommonUtils.normalizeCode(functionCode);
        final Path resDir = trgDir.resolve(resId);
        log.info("Resource dir: {}, exist: {}", resDir.toAbsolutePath(), Files.exists(resDir) );
        if (Files.notExists(resDir)) {
            try {
                Files.createDirectories(resDir);
            }
            catch (IOException e) {
                assetFile.isError = true;
                log.error("028.040 Can't create resource dir: {}", resDir.toAbsolutePath());
                return assetFile;
            }
        }
        assetFile.file = resDir;
        return assetFile;
    }

    private static ExecResult execFileSystemCheck(Path repoDir, TaskParamsYaml.FunctionConfig functionConfig) {
//git>git fsck --full
//Checking object directories: 100% (256/256), done.
//Checking objects: 100% (10432/10432), done.
//error: bad signature 0x00000000
//fatal: index file corrupt

        // git fsck --full
        ExecResult result = execCommonCmd(List.of("git", "-C", repoDir.toAbsolutePath().toString(), "checkout", functionConfig.git.commit),0L);
        return result;
    }

    // ---------------------------------------------------------------------------------------------
    // object store + commit materialization.
    //
    // These serve git-sourced Functions and nothing else. Nothing here checks anything out into a
    // working tree: `objects` is a BARE repo that only ever accumulates objects, and a commit is
    // materialized by reading its tree. That is what lets several revisions of one repo be live at the
    // same time, which one shared working tree could never do.
    // ---------------------------------------------------------------------------------------------

    /** Creates the bare object store if it isn't there yet. Nothing is ever checked out of it. */
    public static void ensureBareRepo(Path objectsDir, String gitUrl, GitData.GitContext gitContext) throws IOException {
        if (Files.exists(objectsDir.resolve("HEAD"))) {
            return;
        }
        Files.createDirectories(objectsDir);
        final ExecResult init = execGitCmd(List.of("git", "init", "--bare", objectsDir.toAbsolutePath().toString()), gitContext);
        if (!init.ok) {
            throw new IOException("028.220 Can't init a bare repo at " + objectsDir.toAbsolutePath() + ", error: " + init.error);
        }
        final ExecResult remote = execGitCmd(
            List.of("git", "-C", objectsDir.toAbsolutePath().toString(), "remote", "add", "origin", gitUrl), gitContext);
        if (!remote.ok) {
            throw new IOException("028.230 Can't add remote " + gitUrl + ", error: " + remote.error);
        }
    }

    /** Whether the object store already holds this commit. Answered locally, no network. */
    public static boolean hasCommit(Path objectsDir, String sha, GitData.GitContext gitContext) {
        final ExecResult result = execGitCmd(
            List.of("git", "-C", objectsDir.toAbsolutePath().toString(), "cat-file", "-e", sha + "^{commit}"), gitContext);
        return result.ok && result.systemExecResult!=null && result.systemExecResult.isOk();
    }

    /**
     * Fetches exactly one commit.
     *
     * <p>❗ `clone` cannot do this - `--branch` resolves ref names only, so `clone --branch &lt;sha&gt;`
     * fails even when the sha IS the branch tip. Only `fetch` accepts an object id, and only when the
     * server allows it (uploadpack.allowReachableSHA1InWant; GitHub and GitLab do).
     */
    public static ExecResult fetchCommit(Path objectsDir, String sha, GitData.GitContext gitContext) {
        final List<String> cmd = List.of(
            "git", "-C", objectsDir.toAbsolutePath().toString(), "fetch", "--depth", "1", "origin", sha);
        log.info("exec {}", cmd);
        return execGitCmd(cmd, gitContext);
    }

    /** Writes a commit's tree into a tar file. `archive` reads objects and touches no working tree. */
    public static ExecResult archiveCommit(Path objectsDir, String sha, Path tarFile, GitData.GitContext gitContext) {
        final List<String> cmd = List.of(
            "git", "-C", objectsDir.toAbsolutePath().toString(), "archive", "--format=tar",
            "--output=" + tarFile.toAbsolutePath(), sha);
        log.info("exec {}", cmd);
        return execGitCmd(cmd, gitContext);
    }

    public static ExecResult execCheckoutRevision(Path repoDir, String commit) {
        // git checkout sha1
        ExecResult result = execCommonCmd(List.of("git", "-C", repoDir.toAbsolutePath().toString(), "checkout", commit),0L);
        return result;
    }

    public static ExecResult execPullOrigin(Path repoDir, String branch) {
        // git pull origin master
        ExecResult result = execCommonCmd(List.of("git", "-C", repoDir.toAbsolutePath().toString(), "pull", "origin", branch),0L);
        return result;
    }

    public static ExecResult execCleanDF(Path repoDir) {
        // git clean -df
        ExecResult result = execCommonCmd(List.of("git", "-C", repoDir.toAbsolutePath().toString(), "clean", "-df"),120L);
        return result;
    }

    public static ExecResult execRevParse(Path repoDir) {
        // git rev-parse --is-inside-work-tree
        ExecResult result = execCommonCmd(List.of("git", "-C", repoDir.toAbsolutePath().toString(), "rev-parse", "--is-inside-work-tree"),60L);
        return result;
    }

    public static ExecResult execConfigEnableLongPaths() {
        // git config --global core.longpaths true
        ExecResult result = execCommonCmd(List.of("git", "config", "--global", "core.longpaths", "true"),60L);
        return result;
    }

    // TODO 2019-05-11 add this before checkout for new changes
    public static ExecResult execResetHardHead(Path repoDir) {
        // git reset --hard HEAD
        ExecResult result = execCommonCmd(List.of("git", "-C", repoDir.toAbsolutePath().toString(), "reset", "--hard", "HEAD"),120L);
        return result;
    }

    private static ExecResult execCommonCmd(List<String> cmd, long timeout) {
        log.info("exec {}", cmd);
        return execGitCmd(cmd, timeout);
    }

    private static String getGitVersion(String s) {
        if (s.startsWith(GIT_VERSION_PREFIX)) {
            return s.substring(GIT_VERSION_PREFIX.length()).strip();
        }
        if (s.startsWith(GIT_PREFIX)) {
            return s.substring(GIT_PREFIX.length()).strip();
        }
        return StringUtils.substring(s, 0, 100);
    }

}

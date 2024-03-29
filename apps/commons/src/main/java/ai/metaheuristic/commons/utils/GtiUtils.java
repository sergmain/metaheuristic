/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

    public static ExecResult execClone(Path repoDir, String gitUrl, GitData.GitContext gitContext) {
        // git -C <path> clone <git-repo-url> git-repo
        List<String> cmd = List.of("git", "-C", repoDir.toAbsolutePath().toString(), "clone", gitUrl, CommonConsts.GIT_REPO);
        log.info("exec {}", cmd);
        ExecResult result = execGitCmd(cmd, gitContext.withTimeout(0L));
        return result;
    }

    @SneakyThrows
    @Nullable
    public static ExecResult initGitRepository(GitInfo gitInfo, Path gitDir, String gitUrl, GitData.GitContext gitContext, boolean firstRun) {

        Path repoDir = gitDir.resolve(CommonConsts.GIT_REPO);
        log.info("028.070 Target dir: {}, exist: {}", repoDir.toAbsolutePath(), Files.exists(repoDir) );

        if (Files.notExists(repoDir)) {
            ExecResult result = execClone(gitDir, gitUrl,  gitContext);
            log.info("028.080 Result of cloning repo: {}", result.toString());
            if (!result.ok || !result.systemExecResult.isOk()) {
                result = tryToRepairRepo(gitDir, gitContext, gitUrl);
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
            result = tryToRepairRepo(repoDir, gitContext, gitUrl);
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

    public static ExecResult tryToRepairRepo(Path gitDir, GitData.GitContext gitContext, String gitUrl) {
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
        result = execClone(gitDir, gitUrl, gitContext);
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

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

package ai.metaheuristic.ai.mhbp.services;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.api.data.GitData;
import ai.metaheuristic.commons.utils.GtiUtils;
import ai.metaheuristic.api.data.AssetFile;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.utils.ArtifactCommonUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.file.PathUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static ai.metaheuristic.commons.system.SystemProcessLauncher.ExecResult;

/**
 * @author Sergio Lissner
 * Date: 4/14/2023
 * Time: 5:42 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class LocalGitSourcingService {

    private static final List<String> GIT_VERSION_CMD = List.of("git", "--version");
    private static final String GIT_VERSION_PREFIX = "git version";
    private static final String GIT_PREFIX = "git";

    private final Globals globals;

    public final GitData.GitStatusInfo gitStatusInfo = new GitData.GitStatusInfo(EnumsApi.GitStatus.unknown);

    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private static final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    public GitData.GitStatusInfo getGitStatus() {
        readLock.lock();
        try {
            if (gitStatusInfo.status!= EnumsApi.GitStatus.unknown) {
                return gitStatusInfo;
            }
        } finally {
            readLock.unlock();
        }
        writeLock.lock();
        try {
            if (gitStatusInfo.status!= EnumsApi.GitStatus.unknown) {
                return gitStatusInfo;
            }
            this.gitStatusInfo.status = EnumsApi.GitStatus.processing;
            Thread t = new Thread(() -> {
                try {
                    this.gitStatusInfo.updateWith( getGitStatusInternal() );
                } catch(Throwable th){
                    this.gitStatusInfo.status = EnumsApi.GitStatus.error;
                    log.error("Git info retrival failed", th);
                }
            });
            t.start();
            return gitStatusInfo;
        } finally {
            writeLock.unlock();
        }
    }

    public GitData.GitStatusInfo getGitStatusInternal() {
        return getGitStatus(new GitData.GitContext(30L, globals.mhbp.max.consoleOutputLines));
    }

    public static GitData.GitStatusInfo getGitStatus(GitData.GitContext gitContext) {
        ExecResult result = GtiUtils.execGitCmd(GIT_VERSION_CMD, gitContext);
        if (!result.ok) {
            log.warn("026.010 Error of getting git status");
            log.warn("\tresult.ok: {}",  result.ok);
            log.warn("\tresult.error: {}",  result.error);
            log.warn("\tresult.functionDir: {}", result.functionDir!=null ? result.functionDir.toAbsolutePath() : null);
            log.warn("\tresult.systemExecResult: {}",  result.systemExecResult);
            return new GitData.GitStatusInfo(EnumsApi.GitStatus.error, null, "026.010 Error: " + result.error);
        }

        // at this point result.systemExecResult must be not null,
        // it can be null only if result.ok==false, but see above
        //noinspection DataFlowIssue
        if (result.systemExecResult.exitCode!=0) {
            return new GitData.GitStatusInfo(
                    EnumsApi.GitStatus.not_found, null,
                    "026.013 Console: " + result.systemExecResult.console);
        }
        return new GitData.GitStatusInfo(EnumsApi.GitStatus.installed, getGitVersion(result.systemExecResult.console.toLowerCase()), null);
    }

    private static AssetFile prepareFunctionDir(final Path resourceDir, String functionCode) {
        final AssetFile assetFile = new AssetFile();
        final Path trgDir = ArtifactCommonUtils.prepareFunctionPath(resourceDir);
        log.info("Target dir: {}, exist: {}", trgDir.toAbsolutePath(), Files.exists(trgDir) );
        if (Files.notExists(trgDir)) {
            try {
                Files.createDirectories(trgDir);
            }
            catch (IOException e) {
                assetFile.isError = true;
                log.error("026.030 Can't create function dir: {}", trgDir.toAbsolutePath());
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
                log.error("026.040 Can't create resource dir: {}", resDir.toAbsolutePath());
                return assetFile;
            }
        }
        assetFile.file = resDir;
        return assetFile;
    }

    @SneakyThrows
    public static ExecResult prepareRepo(final Path resourceDir, GitInfo git, GitData.GitContext gitContext) {

        Path functionDir = resourceDir;
        Path repoDir = functionDir.resolve(CommonConsts.GIT_REPO);
        log.info("026.070 Target dir: {}, exist: {}", repoDir.toAbsolutePath(), Files.exists(repoDir));

        if (Files.exists(repoDir) && PathUtils.isEmpty(repoDir)) {
            Files.delete(repoDir);
        }

        if (Files.notExists(repoDir)) {
            ExecResult result = GtiUtils.execClone(functionDir, git.getRepo(), gitContext);
            log.info("026.080 Result of cloning repo: {}", result.toString());
            if (!result.ok || !result.systemExecResult.isOk()) {
                result = tryToRepairRepo(functionDir, git, gitContext);
                log.info("026.090 Result of repairing of repo: {}", result.toString());
                return result;
            }
        }
        ExecResult result = execRevParse(repoDir, gitContext);
        log.info("026.100 Result of execRevParse: {}", result.toString());
        if (!result.ok) {
            return result;
        }
        if (!result.systemExecResult.isOk) {
            return new ExecResult(null,false, result.systemExecResult.console);
        }
        if (!"true".equals(result.systemExecResult.console.strip())) {
            result = tryToRepairRepo(repoDir, git, gitContext);
            log.info("026.110 Result of tryToRepairRepo: {}", result.toString());
            if (!result.ok) {
                return result;
            }
            if (!result.systemExecResult.isOk) {
                return new ExecResult(null,false, result.systemExecResult.console);
            }
        }

        result = execResetHardHead(repoDir, gitContext);
        log.info("026.120 Result of execResetHardHead: {}", result.toString());
        if (!result.ok) {
            return result;
        }
        if (!result.systemExecResult.isOk) {
            return new ExecResult(null,false, result.systemExecResult.console);
        }

        result = execCleanDF(repoDir, gitContext);
        log.info("026.130 Result of execCleanDF: {}", result.toString());
        if (!result.ok) {
            return result;
        }
        if (!result.systemExecResult.isOk) {
            return new ExecResult(null,false, result.systemExecResult.console);
        }

        result = execPullOrigin(repoDir, git, gitContext);
        log.info("026.140 Result of execPullOrigin: {}", result.toString());
        if (!result.ok) {
            return result;
        }
        if (!result.systemExecResult.isOk) {
            return new ExecResult(null,false, result.systemExecResult.console);
        }

        result = execCheckoutRevision(repoDir, git, gitContext);
        log.info("026.150 Result of execCheckoutRevision: {}", result.toString());
        if (!result.ok) {
            return result;
        }
        if (!result.systemExecResult.isOk) {
            return new ExecResult(null,false, result.systemExecResult.console);
        }
        log.info("026.160 repoDir: {}, exist: {}", repoDir.toAbsolutePath(), Files.exists(repoDir));

        return new ExecResult(repoDir, new FunctionApiData.SystemExecResult(null, true, 0, "" ), true, null);
    }

    @SneakyThrows
    public static ExecResult tryToRepairRepo(Path functionDir, GitInfo git, GitData.GitContext gitContext) {
        Path repoDir = functionDir.resolve(CommonConsts.GIT_REPO);
        ExecResult result;
        PathUtils.deleteDirectory(repoDir);
        //Files.deleteIfExists(repoDir);
        if (Files.exists(repoDir)) {
            return new ExecResult(null,
                    false,
                    "026.170 can't prepare repo dir for function: " + repoDir.toAbsolutePath());
        }
        result = GtiUtils.execClone(functionDir, git.getRepo(), gitContext);
        return result;
    }

    private static ExecResult execFileSystemCheck(Path repoDir, Globals.Git git, GitData.GitContext gitContext) {
//git>git fsck --full
//Checking object directories: 100% (256/256), done.
//Checking objects: 100% (10432/10432), done.
//error: bad signature 0x00000000
//fatal: index file corrupt

        // git fsck --full
        ExecResult result = execCommonCmd(List.of("git", "-C", repoDir.toAbsolutePath().toString(), "checkout", git.commit), gitContext.withTimeout(0L));
        return result;
    }

    private static ExecResult execCheckoutRevision(Path repoDir, GitInfo git, GitData.GitContext gitContext) {
        // git checkout sha1
        ExecResult result = execCommonCmd(List.of("git", "-C", repoDir.toAbsolutePath().toString(), "checkout", git.getCommit()), gitContext.withTimeout(0L));
        return result;
    }

    private static ExecResult execPullOrigin(Path repoDir, GitInfo git, GitData.GitContext gitContext) {
        // pull origin master
        ExecResult result = execCommonCmd(List.of("git", "-C", repoDir.toAbsolutePath().toString(), "pull", "origin", git.getBranch()), gitContext.withTimeout(0L));
        return result;
    }

    private static ExecResult execCleanDF(Path repoDir, GitData.GitContext gitContext) {
        // git clean -df
        ExecResult result = execCommonCmd(List.of("git", "-C", repoDir.toAbsolutePath().toString(), "clean", "-df"), gitContext.withTimeout(120L));
        return result;
    }

    private static ExecResult execRevParse(Path repoDir, GitData.GitContext gitContext) {
        // git rev-parse --is-inside-work-tree
        ExecResult result = execCommonCmd(List.of("git", "-C", repoDir.toAbsolutePath().toString(), "rev-parse", "--is-inside-work-tree"), gitContext.withTimeout(60L));
        return result;
    }

    // TODO 2019-05-11 add this before checkout for new changes
    private static ExecResult execResetHardHead(Path repoDir, GitData.GitContext gitContext) {
        // git reset --hard HEAD
        ExecResult result = execCommonCmd(List.of("git", "-C", repoDir.toAbsolutePath().toString(), "reset", "--hard", "HEAD"), gitContext.withTimeout(120L));
        return result;
    }

    private static ExecResult execCommonCmd(List<String> cmd, GitData.GitContext gitContext) {
        log.info("exec {}", cmd);
        return GtiUtils.execGitCmd(cmd, gitContext);
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

/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.processor.sourcing.git;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.core.SystemProcessLauncher;
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import ai.metaheuristic.ai.utils.asset.AssetFile;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
@Slf4j
@Profile("processor")
public class GitSourcingService {

    private static final List<String> GIT_VERSION_CMD = List.of("git", "--version");
    private static final String GIT_VERSION_PREFIX = "git version";
    private static final String GIT_PREFIX = "git";

    private final ProcessorEnvironment processorEnvironment;
    private final Globals globals;

    public final GitStatusInfo gitStatusInfo;

    public GitSourcingService(ProcessorEnvironment processorEnvironment, Globals globals) {
        this.processorEnvironment = processorEnvironment;
        this.globals = globals;
        this.gitStatusInfo = getGitStatus();
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
        public Enums.GitStatus status;
        public String version;
        public String error;

        public GitStatusInfo(Enums.GitStatus status) {
            this.status = status;
        }
    }

    public GitStatusInfo getGitStatus() {
        SystemProcessLauncher.ExecResult result = execGitCmd(GIT_VERSION_CMD, 30L);
        if (!result.ok) {
            log.warn("#027.010 Error of getting git status");
            log.warn("\tresult.ok: {}",  result.ok);
            log.warn("\tresult.error: {}",  result.error);
            log.warn("\tresult.functionDir: {}",  result.functionDir !=null ? result.functionDir.toAbsolutePath() : null);
            log.warn("\tresult.systemExecResult: {}",  result.systemExecResult);
            return new GitStatusInfo(Enums.GitStatus.error, null, "#027.010 Error: " + result.error);
        }

        // at this point result.systemExecResult must be not null, it can be null only if result.ok==false, but see above
        if (result.systemExecResult.exitCode!=0) {
            return new GitStatusInfo(
                    Enums.GitStatus.not_found, null,
                    "#027.013 Console: " + result.systemExecResult.console);
        }
        return new GitStatusInfo(Enums.GitStatus.installed, getGitVersion(result.systemExecResult.console.toLowerCase()), null);
    }

    public SystemProcessLauncher.ExecResult execGitCmd(List<String> gitVersionCmd, long timeout) {
        return SystemProcessLauncher.execCmd(gitVersionCmd, timeout, globals.processor.taskConsoleOutputMaxLines);
    }

    private static AssetFile prepareFunctionDir(final Path resourceDir, String functionCode) {
        final AssetFile assetFile = new AssetFile();
        final Path trgDir = resourceDir.resolve(EnumsApi.DataType.function.toString());
        log.info("Target dir: {}, exist: {}", trgDir.toAbsolutePath(), Files.exists(trgDir));
        if (Files.notExists(trgDir)) {
            try {
                Files.createDirectories(trgDir);
            }
            catch (IOException e) {
                assetFile.isError = true;
                log.error("#027.030 Can't create function dir: {}", trgDir.toAbsolutePath());
                return assetFile;
            }
        }
        final String resId = functionCode.replace(':', '_');
        final Path resDir = trgDir.resolve(resId);
        log.info("Resource dir: {}, exist: {}", resDir.toAbsolutePath(), Files.exists(resDir) );
        if (Files.notExists(resDir)) {
            try {
                Files.createDirectories(resDir);
            }
            catch (IOException e) {
                assetFile.isError = true;
                log.error("#027.040 Can't create resource dir: {}", resDir.toAbsolutePath());
                return assetFile;
            }
        }
        assetFile.file = resDir;
        return assetFile;
    }

    public SystemProcessLauncher.ExecResult prepareFunction(final Path resourceDir, TaskParamsYaml.FunctionConfig functionConfig) {

        log.info("#027.050 Start preparing function dir");
        AssetFile assetFile = prepareFunctionDir(resourceDir, functionConfig.code);
        log.info("#027.060 assetFile.isError: {}" , assetFile.isError);
        if (assetFile.isError) {
            return new SystemProcessLauncher.ExecResult(null,false, "#027.060 Can't create dir for function " + functionConfig.code);
        }

        Path functionDir = assetFile.file;
        Path repoDir = functionDir.resolve("git");
        log.info("#027.070 Target dir: {}, exist: {}", repoDir.toAbsolutePath(), Files.exists(repoDir) );

        if (Files.notExists(repoDir)) {
            SystemProcessLauncher.ExecResult result = execClone(functionDir, functionConfig);
            log.info("#027.080 Result of cloning repo: {}", result.toString());
            if (!result.ok || !result.systemExecResult.isOk()) {
                result = tryToRepairRepo(functionDir, functionConfig);
                log.info("#027.090 Result of repairing of repo: {}", result.toString());
                return result;
            }
        }
        SystemProcessLauncher.ExecResult result = execRevParse(repoDir);
        log.info("#027.100 Result of execRevParse: {}", result.toString());
        if (!result.ok) {
            return result;
        }
        if (!result.systemExecResult.isOk) {
            return new SystemProcessLauncher.ExecResult(null,false, result.systemExecResult.console);
        }
        if (!"true".equals(result.systemExecResult.console.strip())) {
            result = tryToRepairRepo(repoDir, functionConfig);
            log.info("#027.110 Result of tryToRepairRepo: {}", result.toString());
            if (!result.ok) {
                return result;
            }
            if (!result.systemExecResult.isOk) {
                return new SystemProcessLauncher.ExecResult(null,false, result.systemExecResult.console);
            }
        }

        result = execResetHardHead(repoDir);
        log.info("#027.120 Result of execResetHardHead: {}", result.toString());
        if (!result.ok) {
            return result;
        }
        if (!result.systemExecResult.isOk) {
            return new SystemProcessLauncher.ExecResult(null,false, result.systemExecResult.console);
        }

        result = execCleanDF(repoDir);
        log.info("#027.130 Result of execCleanDF: {}", result.toString());
        if (!result.ok) {
            return result;
        }
        if (!result.systemExecResult.isOk) {
            return new SystemProcessLauncher.ExecResult(null,false, result.systemExecResult.console);
        }

        result = execPullOrigin(repoDir, functionConfig);
        log.info("#027.140 Result of execPullOrigin: {}", result.toString());
        if (!result.ok) {
            return result;
        }
        if (!result.systemExecResult.isOk) {
            return new SystemProcessLauncher.ExecResult(null,false, result.systemExecResult.console);
        }

        result = execCheckoutRevision(repoDir, functionConfig);
        log.info("#027.150 Result of execCheckoutRevision: {}", result.toString());
        if (!result.ok) {
            return result;
        }
        if (!result.systemExecResult.isOk) {
            return new SystemProcessLauncher.ExecResult(null,false, result.systemExecResult.console);
        }
        log.info("#027.160 repoDir: {}, exist: {}", repoDir.toAbsolutePath(), Files.exists(repoDir));

        return new SystemProcessLauncher.ExecResult(repoDir, new FunctionApiData.SystemExecResult(functionConfig.code, true, 0, "" ), true, null);
    }

    public SystemProcessLauncher.ExecResult tryToRepairRepo(Path functionDir, TaskParamsYaml.FunctionConfig functionConfig) {
        Path repoDir = functionDir.resolve("git");
        SystemProcessLauncher.ExecResult result;
        try {
            Files.deleteIfExists(repoDir);
        }
        catch (IOException e) {
            //
        }
        if (Files.exists(repoDir)) {
            return new SystemProcessLauncher.ExecResult(null,
                    false,
                    "#027.170 Function "+functionConfig.code+", can't prepare repo dir for function: " + repoDir.toAbsolutePath());
        }
        result = execClone(functionDir, functionConfig);
        return result;
    }

    private SystemProcessLauncher.ExecResult execFileSystemCheck(Path repoDir, TaskParamsYaml.FunctionConfig functionConfig) {
//git>git fsck --full
//Checking object directories: 100% (256/256), done.
//Checking objects: 100% (10432/10432), done.
//error: bad signature 0x00000000
//fatal: index file corrupt

        // git fsck --full
        SystemProcessLauncher.ExecResult result = execCommonCmd(List.of("git", "-C", repoDir.toAbsolutePath().toString(), "checkout", functionConfig.git.commit),0L);
        return result;
    }

    private SystemProcessLauncher.ExecResult execCheckoutRevision(Path repoDir, TaskParamsYaml.FunctionConfig functionConfig) {
        // git checkout sha1
        SystemProcessLauncher.ExecResult result = execCommonCmd(List.of("git", "-C", repoDir.toAbsolutePath().toString(), "checkout", functionConfig.git.commit),0L);
        return result;
    }

    private SystemProcessLauncher.ExecResult execPullOrigin(Path repoDir, TaskParamsYaml.FunctionConfig functionConfig) {
        // pull origin master
        SystemProcessLauncher.ExecResult result = execCommonCmd(List.of("git", "-C", repoDir.toAbsolutePath().toString(), "pull", "origin", functionConfig.git.branch),0L);
        return result;
    }

    private SystemProcessLauncher.ExecResult execCleanDF(Path repoDir) {
        // git clean -df
        SystemProcessLauncher.ExecResult result = execCommonCmd(List.of("git", "-C", repoDir.toAbsolutePath().toString(), "clean", "-df"),120L);
        return result;
    }

    private SystemProcessLauncher.ExecResult execRevParse(Path repoDir) {
        // git rev-parse --is-inside-work-tree
        SystemProcessLauncher.ExecResult result = execCommonCmd(List.of("git", "-C", repoDir.toAbsolutePath().toString(), "rev-parse", "--is-inside-work-tree"),60L);
        return result;
    }

    // TODO 2019-05-11 add this before checkout for new changes
    private SystemProcessLauncher.ExecResult execResetHardHead(Path repoDir) {
        // git reset --hard HEAD
        SystemProcessLauncher.ExecResult result = execCommonCmd(List.of("git", "-C", repoDir.toAbsolutePath().toString(), "reset", "--hard", "HEAD"),120L);
        return result;
    }

    private SystemProcessLauncher.ExecResult execCommonCmd(List<String> cmd, long timeout) {
        log.info("exec {}", cmd);
        return execGitCmd(cmd, timeout);
    }

    private SystemProcessLauncher.ExecResult execClone(Path repoDir, TaskParamsYaml.FunctionConfig functionConfig) {
        // git -C <path> clone <git-repo-url> git

        String mirror = processorEnvironment.envParams.getEnvParamsYaml().mirrors.get(functionConfig.git.repo);
        String gitUrl = mirror!=null ? mirror : functionConfig.git.repo;
        List<String> cmd = List.of("git", "-C", repoDir.toAbsolutePath().toString(), "clone", gitUrl, "git");
        log.info("exec {}", cmd);
        SystemProcessLauncher.ExecResult result = execGitCmd(cmd, 0L);
        return result;
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

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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.commons.system.SystemProcessLauncher;
import ai.metaheuristic.api.data.GitData;
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import ai.metaheuristic.commons.utils.GtiUtils;
import ai.metaheuristic.api.data.AssetFile;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.file.PathUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Slf4j
@Profile("processor")
public class GitSourcingService {

    private final ProcessorEnvironment processorEnvironment;
    private final Globals globals;

    public GtiUtils.GitStatusInfo gitStatusInfo;

    public GitSourcingService(@Autowired ProcessorEnvironment processorEnvironment, @Autowired Globals globals) {
        this.processorEnvironment = processorEnvironment;
        this.globals = globals;
        GtiUtils.taskConsoleOutputMaxLines = globals.processor.taskConsoleOutputMaxLines;
        Thread.startVirtualThread(this::intiGitStatus);
    }

    public void intiGitStatus() {
        this.gitStatusInfo = GtiUtils.getGitStatus();
    }

    public SystemProcessLauncher.ExecResult prepareFunction(final Path resourceDir, TaskParamsYaml.FunctionConfig functionConfig) {

        log.info("028.050 Start preparing function dir");
        AssetFile assetFile = GtiUtils.prepareFunctionDir(resourceDir, functionConfig.code);
        log.info("028.060 assetFile.isError: {}" , assetFile.isError);
        if (assetFile.isError) {
            return new SystemProcessLauncher.ExecResult(null,false, "028.060 Can't create dir for function " + functionConfig.code);
        }

        Path functionDir = assetFile.file;
        Path repoDir = functionDir.resolve("git");
        log.info("028.070 Target dir: {}, exist: {}", repoDir.toAbsolutePath(), Files.exists(repoDir) );

        if (Files.notExists(repoDir)) {
            SystemProcessLauncher.ExecResult result = execClone(functionDir, functionConfig);
            log.info("028.080 Result of cloning repo: {}", result.toString());
            if (!result.ok || !result.systemExecResult.isOk()) {
                result = tryToRepairRepo(functionDir, functionConfig);
                log.info("028.090 Result of repairing of repo: {}", result.toString());
                return result;
            }
        }
        SystemProcessLauncher.ExecResult result = GtiUtils.execRevParse(repoDir);
        log.info("028.100 Result of execRevParse: {}", result.toString());
        if (!result.ok) {
            return result;
        }
        if (!result.systemExecResult.isOk) {
            return new SystemProcessLauncher.ExecResult(null,false, result.systemExecResult.console);
        }
        if (!"true".equals(result.systemExecResult.console.strip())) {
            result = tryToRepairRepo(repoDir, functionConfig);
            log.info("028.110 Result of tryToRepairRepo: {}", result.toString());
            if (!result.ok) {
                return result;
            }
            if (!result.systemExecResult.isOk) {
                return new SystemProcessLauncher.ExecResult(null,false, result.systemExecResult.console);
            }
        }

        result = GtiUtils.execResetHardHead(repoDir);
        log.info("028.120 Result of execResetHardHead: {}", result.toString());
        if (!result.ok) {
            return result;
        }
        if (!result.systemExecResult.isOk) {
            return new SystemProcessLauncher.ExecResult(null,false, result.systemExecResult.console);
        }

        result = GtiUtils.execCleanDF(repoDir);
        log.info("028.130 Result of execCleanDF: {}", result.toString());
        if (!result.ok) {
            return result;
        }
        if (!result.systemExecResult.isOk) {
            return new SystemProcessLauncher.ExecResult(null,false, result.systemExecResult.console);
        }

        result = GtiUtils.execPullOrigin(repoDir, functionConfig);
        log.info("028.140 Result of execPullOrigin: {}", result.toString());
        if (!result.ok) {
            return result;
        }
        if (!result.systemExecResult.isOk) {
            return new SystemProcessLauncher.ExecResult(null,false, result.systemExecResult.console);
        }

        result = GtiUtils.execCheckoutRevision(repoDir, functionConfig);
        log.info("028.150 Result of execCheckoutRevision: {}", result.toString());
        if (!result.ok) {
            return result;
        }
        if (!result.systemExecResult.isOk) {
            return new SystemProcessLauncher.ExecResult(null,false, result.systemExecResult.console);
        }
        log.info("028.160 repoDir: {}, exist: {}", repoDir.toAbsolutePath(), Files.exists(repoDir));

        return new SystemProcessLauncher.ExecResult(repoDir, new FunctionApiData.SystemExecResult(functionConfig.code, true, 0, "" ), true, null);
    }

    public SystemProcessLauncher.ExecResult tryToRepairRepo(Path functionDir, TaskParamsYaml.FunctionConfig functionConfig) {
        Path repoDir = functionDir.resolve("git");
        SystemProcessLauncher.ExecResult result;
        try {
            PathUtils.deleteDirectory(repoDir);
        }
        catch (IOException e) {
            //
        }
        if (Files.exists(repoDir)) {
            return new SystemProcessLauncher.ExecResult(null,
                false,
                "028.170 Function "+functionConfig.code+", can't prepare repo dir for function: " + repoDir.toAbsolutePath());
        }
        result = execClone(functionDir, functionConfig);
        return result;
    }

    private SystemProcessLauncher.ExecResult execClone(Path repoDir, TaskParamsYaml.FunctionConfig functionConfig) {
        // git -C <path> clone <git-repo-url> git
        String mirror = processorEnvironment.envParams.getEnvParamsYaml().mirrors.get(functionConfig.git.repo);
        String gitUrl = mirror!=null ? mirror : functionConfig.git.repo;

        SystemProcessLauncher.ExecResult result = GtiUtils.execClone(repoDir, gitUrl,  new GitData.GitContext(60L, globals.mhbp.max.consoleOutputLines));
        return result;
    }

}

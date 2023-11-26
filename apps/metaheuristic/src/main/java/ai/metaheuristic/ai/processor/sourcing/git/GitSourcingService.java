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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

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

        String mirror = processorEnvironment.envParams.getEnvParamsYaml().mirrors.get(functionConfig.git.repo);
        String gitUrl = mirror!=null ? mirror : functionConfig.git.repo;
        GitData.GitContext gitContext = new GitData.GitContext(60L, globals.mhbp.max.consoleOutputLines);

        final SystemProcessLauncher.ExecResult execResult = GtiUtils.initGitRepository(functionConfig.git, functionDir, gitUrl, gitContext, true);
        if (execResult!=null) {
            return execResult;
        }
        return new SystemProcessLauncher.ExecResult(functionDir, new FunctionApiData.SystemExecResult(functionConfig.code, true, 0, ""), true, null);
    }

}

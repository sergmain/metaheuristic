/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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
package ai.metaheuristic.ai.station;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.comm.Protocol;
import ai.metaheuristic.ai.core.ExecProcessService;
import ai.metaheuristic.ai.resource.AssetFile;
import ai.metaheuristic.ai.resource.ResourceUtils;
import ai.metaheuristic.ai.station.env.EnvService;
import ai.metaheuristic.ai.station.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.station.station_resource.ResourceProvider;
import ai.metaheuristic.ai.station.station_resource.ResourceProviderFactory;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.station_task.StationTask;
import ai.metaheuristic.ai.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.data.SnippetApiData;
import ai.metaheuristic.api.v1.data.task.TaskParamsYaml;
import ai.metaheuristic.api.v1.data_storage.DataStorageParams;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Profile("station")
public class TaskProcessor {

    private final Globals globals;

    private final ExecProcessService execProcessService;
    private final StationTaskService stationTaskService;
    private final CurrentExecState currentExecState;
    private final LaunchpadLookupExtendedService launchpadLookupExtendedService ;
    private final MetadataService metadataService;
    private final EnvService envService;
    private final StationService stationService;
    private final ResourceProviderFactory resourceProviderFactory;
    private final GitSourcingService gitSourcingService;

    @Data
    public static class SnippetPrepareResult {
        public SnippetApiData.SnippetConfig snippet;
        public AssetFile snippetAssetFile;
        public SnippetApiData.SnippetExecResult snippetExecResult;
        boolean isLoaded = true;
    }

    public TaskProcessor(Globals globals, ExecProcessService execProcessService, StationTaskService stationTaskService, CurrentExecState currentExecState, LaunchpadLookupExtendedService launchpadLookupExtendedService, MetadataService metadataService, EnvService envService, StationService stationService, ResourceProviderFactory resourceProviderFactory, GitSourcingService gitSourcingService) {
        this.globals = globals;
        this.execProcessService = execProcessService;
        this.stationTaskService = stationTaskService;
        this.currentExecState = currentExecState;
        this.launchpadLookupExtendedService = launchpadLookupExtendedService;
        this.metadataService = metadataService;
        this.envService = envService;
        this.stationService = stationService;
        this.resourceProviderFactory = resourceProviderFactory;
        this.gitSourcingService = gitSourcingService;
    }

    @SuppressWarnings("Duplicates")
    public void fixedDelay() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }

        List<StationTask> tasks = stationTaskService.findAllByFinishedOnIsNullAndAssetsPreparedIs(true);
        for (StationTask task : tasks) {
//            if (task.isDelivered()) {
//                continue;
//            }
            log.info("Start processing task {}", task);
            final Metadata.LaunchpadInfo launchpadCode = metadataService.launchpadUrlAsCode(task.launchpadUrl);

            if (StringUtils.isBlank(task.launchpadUrl)) {
                stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, "Broken task. LaunchpadUrl is blank.");
                continue;
            }
            if (StringUtils.isBlank(task.launchpadUrl)) {
                stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, "Broken task. Launchpad wasn't found for url "+ task.launchpadUrl);
                continue;
            }

            LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad = launchpadLookupExtendedService.lookupExtendedMap.get(task.launchpadUrl);

            if (launchpad.schedule.isCurrentTimeInactive()) {
                log.info("Can't process task for url {} at this time, time: {}, permitted period of time: {}", task.launchpadUrl, new Date(), launchpad.schedule.asString);
                return;
            }

            if (StringUtils.isBlank(task.getParams())) {
                log.warn("#100.10 Params for task {} is blank", task.getTaskId());
                continue;
            }
            EnumsApi.WorkbookExecState state = currentExecState.getState(task.launchpadUrl, task.workbookId);
            if (state== EnumsApi.WorkbookExecState.UNKNOWN) {
                log.info("The state for Workbook #{}, host {} is unknown, skip it", task.workbookId, task.launchpadUrl);
                continue;
            }

            if (state!= EnumsApi.WorkbookExecState.STARTED) {
                log.info("The state for Workbook #{}, host: {}, is {}, skip it", task.workbookId, task.launchpadUrl, state);
                continue;
            }

            File taskDir = stationTaskService.prepareTaskDir(task.launchpadUrl, task.taskId);

            final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());

            StationService.ResultOfChecking resultOfChecking = stationService.checkForPreparingOfAssets(task, launchpadCode, taskParamYaml, launchpad, taskDir);
            if (resultOfChecking.isError) {
                continue;
            }
            boolean isAllLoaded = resultOfChecking.isAllLoaded;
            for (Map.Entry<String, List<AssetFile>> entry : resultOfChecking.assetFiles.entrySet()) {
                for (AssetFile assetFile : entry.getValue()) {
                    taskParamYaml.taskYaml.inputResourceAbsolutePaths
                            .computeIfAbsent(entry.getKey(), o-> new ArrayList<>())
                            .add(assetFile.file.getAbsolutePath());
                }
            }
            File outputResourceFile = stationService.getOutputResourceFile(task, taskParamYaml, launchpad, taskDir);
            if (outputResourceFile==null) {
                stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, "Broken task. Can't create outputResourceFile");
                continue;
            }
            DataStorageParams dsp = taskParamYaml.taskYaml.getResourceStorageUrls().get(taskParamYaml.taskYaml.outputResourceCode);
            if (dsp==null) {
                stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, "Broken task. Can't find params for outputResourceCode");
                continue;
            }
            switch(dsp.sourcing) {
                case launchpad:
                    taskParamYaml.taskYaml.outputResourceAbsolutePath = outputResourceFile.getAbsolutePath();
                    break;
                case disk:
                    if (dsp.disk!=null && StringUtils.isNotBlank(dsp.disk.path)) {
                        File f = new File(outputResourceFile.getParent(), dsp.disk.path);
                        taskParamYaml.taskYaml.outputResourceAbsolutePath = f.getAbsolutePath();
                    }
                    else {
                        taskParamYaml.taskYaml.outputResourceAbsolutePath = outputResourceFile.getAbsolutePath();
                    }
                    break;
                case git:
                    stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId,
                            "Git sourcing isn't implemented yet");
                    continue;
                default:
                    stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId,
                            "Unknown sourcing type: " + dsp.sourcing);
                    continue;
            }
            if (taskParamYaml.taskYaml.snippet==null) {
                stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, "Broken task. Snippet isn't defined");
                continue;
            }

            File artifactDir = stationTaskService.prepareTaskSubDir(taskDir, Consts.ARTIFACTS_DIR);
            if (artifactDir == null) {
                stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, "Error of configuring of environment. 'artifacts' directory wasn't created, task can't be processed.");
                continue;
            }

            File systemDir = stationTaskService.prepareTaskSubDir(taskDir, Consts.SYSTEM_DIR);
            if (systemDir == null) {
                stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, "Error of configuring of environment. 'system' directory wasn't created, task can't be processed.");
                continue;
            }

            final File paramFile = prepareParamsFileForTask(taskDir, taskParamYaml);
            if (paramFile == null) {
                continue;
            }

            boolean isNotReady = false;
            final SnippetPrepareResult[] results = new SnippetPrepareResult[ totalCountOfSnippets(taskParamYaml.taskYaml) ];
            int idx = 0;
            SnippetPrepareResult result;
            for (SnippetApiData.SnippetConfig preSnippetConfig : taskParamYaml.taskYaml.preSnippets) {
                result = prepareSnippet(launchpadCode, preSnippetConfig);
                if (!result.isLoaded || !isAllLoaded) {
                    isNotReady = true;
                    break;
                }
                results[idx++] = result;
            }
            if (isNotReady) {
                continue;
            }

            result = prepareSnippet(launchpadCode, taskParamYaml.taskYaml.getSnippet());
            results[idx++] = result;
            if (!result.isLoaded || !isAllLoaded) {
                continue;
            }

            for (SnippetApiData.SnippetConfig postSnippetConfig : taskParamYaml.taskYaml.postSnippets) {
                result = prepareSnippet(launchpadCode, postSnippetConfig);
                if (!result.isLoaded) {
                    isNotReady = true;
                    break;
                }
                results[idx++] = result;
            }
            if (isNotReady) {
                continue;
            }

            // at this point all required resources have to be prepared

            task = stationTaskService.setLaunchOn(task.launchpadUrl, task.taskId);

            List<SnippetApiData.SnippetExecResult> preSnippetExecResult = new ArrayList<>();
            List<SnippetApiData.SnippetExecResult> postSnippetExecResult = new ArrayList<>();
            boolean isOk = true;
            idx = 0;
            for (SnippetApiData.SnippetConfig preSnippetConfig : taskParamYaml.taskYaml.preSnippets) {
                result = results[idx++];
                SnippetApiData.SnippetExecResult execResult;
                if (result==null) {
                    execResult = new SnippetApiData.SnippetExecResult(
                            preSnippetConfig.code, false, -999,
                            "Illegal State, result of preparing of snippet "+preSnippetConfig.code+" is null");
                }
                else {
                    execResult = execSnippet(task, taskDir, taskParamYaml, systemDir, paramFile, result);
                }
                preSnippetExecResult.add(execResult);
                if (!execResult.isOk) {
                    isOk = false;
                    break;
                }
            }
            SnippetApiData.SnippetExecResult snippetExecResult = null;
            SnippetApiData.SnippetExecResult generalExec = null;
            if (isOk) {
                result = results[idx++];
                if (result==null) {
                    snippetExecResult = new SnippetApiData.SnippetExecResult(
                            taskParamYaml.taskYaml.getSnippet().code, false, -999,
                            "Illegal State, result of preparing of snippet "+taskParamYaml.taskYaml.getSnippet()+" is null");
                    isOk = false;
                }
                if (isOk) {
                    SnippetApiData.SnippetConfig mainSnippetConfig = result.snippet;
                    snippetExecResult = execSnippet(task, taskDir, taskParamYaml, systemDir, paramFile, result);
                    if (!snippetExecResult.isOk) {
                        isOk = false;
                    }
                    if (isOk) {
                        for (SnippetApiData.SnippetConfig postSnippetConfig : taskParamYaml.taskYaml.postSnippets) {
                            result = results[idx++];
                            SnippetApiData.SnippetExecResult execResult;
                            if (result==null) {
                                execResult = new SnippetApiData.SnippetExecResult(
                                        postSnippetConfig.code, false, -999,
                                        "Illegal State, result of preparing of snippet "+postSnippetConfig.code+" is null");
                            }
                            else {
                                execResult = execSnippet(task, taskDir, taskParamYaml, systemDir, paramFile, result);
                            }
                            postSnippetExecResult.add(execResult);
                            if (!execResult.isOk) {
                                isOk = false;
                                break;
                            }
                        }
                        if (isOk && snippetExecResult.isOk()) {
                            stationTaskService.storeMetrics(task.launchpadUrl, task, mainSnippetConfig, artifactDir);

                            final DataStorageParams params = taskParamYaml.taskYaml.resourceStorageUrls.get(taskParamYaml.taskYaml.outputResourceCode);
                            ResourceProvider resourceProvider = resourceProviderFactory.getResourceProvider(params.sourcing);
                            generalExec = resourceProvider.processResultingFile(
                                    launchpad, task, launchpadCode,
                                    new File(taskParamYaml.taskYaml.outputResourceAbsolutePath),
                                    mainSnippetConfig

                            );
                        }
                    }
                }
            }

            stationTaskService.markAsFinished(task.launchpadUrl, task.getTaskId(),
                    new SnippetApiData.SnippetExec(snippetExecResult, preSnippetExecResult, postSnippetExecResult, generalExec));
        }
    }

    public File prepareParamsFileForTask(File taskDir, TaskParamsYaml taskParamYaml) {
        taskParamYaml.taskYaml.workingPath = taskDir.getAbsolutePath();
        final String params = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(taskParamYaml);
        // persist params.yaml file
        final File paramFile = prepareParamFile(taskDir, params);
        if (paramFile == null) {
            log.warn("#100.20 param file wasn't created, task dir: {}" , taskDir.getAbsolutePath());
            return null;
        }
        return paramFile;
    }

    private int totalCountOfSnippets(TaskParamsYaml.TaskYaml taskYaml) {
        int count = 0;
        count += (taskYaml.preSnippets!=null) ? taskYaml.preSnippets.size() : 0;
        count += (taskYaml.postSnippets!=null) ? taskYaml.postSnippets.size() : 0;
        count += (taskYaml.snippet!=null) ? 1 : 0;
        return count;
    }

    @SuppressWarnings("WeakerAccess")
    // TODO 2019.05.02 implement unit-test for this method
    public SnippetApiData.SnippetExecResult execSnippet(
            StationTask task, File taskDir, TaskParamsYaml taskParamYaml, File systemDir, File paramFile, SnippetPrepareResult snippetPrepareResult) {
        List<String> cmd;
        Interpreter interpreter=null;
        if (StringUtils.isNotBlank(snippetPrepareResult.snippet.env)) {
            interpreter = new Interpreter(envService.getEnvYaml().getEnvs().get(snippetPrepareResult.snippet.env));
            if (interpreter.list == null) {
                log.warn("Can't process the task, the interpreter wasn't found for env: {}", snippetPrepareResult.snippet.env);
                return null;
            }
            cmd = Arrays.stream(interpreter.list).collect(Collectors.toList());
        }
        else {
            // snippet.file is executable file
            cmd = new ArrayList<>();
        }

        log.info("All systems are checked for the task #{}, lift off", task.taskId );

        SnippetApiData.SnippetExecResult snippetExecResult;
        try {

            switch (snippetPrepareResult.snippet.sourcing) {

                case launchpad:
                case git:
                    if (snippetPrepareResult.snippetAssetFile==null) {
                        throw new IllegalStateException("snippetAssetFile is null");
                    }
                    cmd.add(snippetPrepareResult.snippetAssetFile.file.getAbsolutePath());

                    break;
                case station:
                    if (snippetPrepareResult.snippet.file!=null) {
                        //noinspection UseBulkOperation
                        Arrays.stream(StringUtils.split(snippetPrepareResult.snippet.file)).forEachOrdered(cmd::add);
                    }
                    break;
                default:
                    throw new IllegalStateException("Unknown sourcing: "+snippetPrepareResult.snippet.sourcing );
            }

            if (!snippetPrepareResult.snippet.skipParams) {
                if (StringUtils.isNoneBlank(snippetPrepareResult.snippet.params)) {
                    //noinspection UseBulkOperation
                    Arrays.stream(StringUtils.split(snippetPrepareResult.snippet.params)).forEachOrdered(cmd::add);
                }
                cmd.add(paramFile.getAbsolutePath());
            }

            File consoleLogFile = new File(systemDir, Consts.SYSTEM_CONSOLE_OUTPUT_FILE_NAME);

            // Exec snippet
            snippetExecResult = execProcessService.execCommand(
                    cmd, taskDir, consoleLogFile, taskParamYaml.taskYaml.timeoutBeforeTerminate, snippetPrepareResult.snippet.code);

        } catch (Throwable th) {
            log.error("Error exec process:\n" +
                    "\tenv: " + snippetPrepareResult.snippet.env +"\n" +
                    "\tinterpreter: " + interpreter+"\n" +
                    "\tfile: " + (snippetPrepareResult.snippetAssetFile!=null && snippetPrepareResult.snippetAssetFile.file!=null
                    ? snippetPrepareResult.snippetAssetFile.file.getAbsolutePath()
                    : snippetPrepareResult.snippet.file) +"\n" +
                    "\tparams", th);
            snippetExecResult = new SnippetApiData.SnippetExecResult(
                    snippetPrepareResult.snippet.code, false, -1, ExceptionUtils.getStackTrace(th));
        }
        return snippetExecResult;
    }

    @SuppressWarnings("WeakerAccess")
    // TODO 2019.05.02 implement unit-test for this method
    public SnippetPrepareResult prepareSnippet(Metadata.LaunchpadInfo launchpadCode, SnippetApiData.SnippetConfig snippet) {
        SnippetPrepareResult snippetPrepareResult = new SnippetPrepareResult();
        snippetPrepareResult.snippet = snippet;

        if (snippetPrepareResult.snippet.sourcing== EnumsApi.SnippetSourcing.launchpad) {
            final File snippetDir = stationTaskService.prepareSnippetDir(launchpadCode);
            snippetPrepareResult.snippetAssetFile = ResourceUtils.prepareSnippetFile(snippetDir, snippetPrepareResult.snippet.getCode(), snippetPrepareResult.snippet.file);
            // is this snippet prepared?
            if (snippetPrepareResult.snippetAssetFile.isError || !snippetPrepareResult.snippetAssetFile.isContent) {
                log.info("Snippet {} hasn't been prepared yet, {}", snippetPrepareResult.snippet.code, snippetPrepareResult.snippetAssetFile);
                snippetPrepareResult.isLoaded = false;
            }
        }
        else if (snippetPrepareResult.snippet.sourcing==EnumsApi.SnippetSourcing.git) {
            final File snippetRootDir = stationTaskService.prepareSnippetDir(launchpadCode);
            log.info("Root dir for snippet: " + snippetRootDir);
            GitSourcingService.GitExecResult result = gitSourcingService.prepareSnippet(snippetRootDir, snippetPrepareResult.snippet);
            if (result.isError) {
                log.warn("Snippet {} has a permanent error, {}", snippetPrepareResult.snippet.code, result.error);
                snippetPrepareResult.snippetExecResult = new SnippetApiData.SnippetExecResult(snippet.code, false, -1, result.error);
                snippetPrepareResult.isLoaded = false;
                return snippetPrepareResult;
            }
            snippetPrepareResult.snippetAssetFile = new AssetFile();
            snippetPrepareResult.snippetAssetFile.file = new File(result.snippetDir, snippetPrepareResult.snippet.file);
            log.info("Snippet asset file: {}, exist: {}", snippetPrepareResult.snippetAssetFile.file.getAbsolutePath(), snippetPrepareResult.snippetAssetFile.file.exists() );
        }
        return snippetPrepareResult;
    }

    private File prepareParamFile(File taskDir, String params) {
        File artifactDir = stationTaskService.prepareTaskSubDir(taskDir, Consts.ARTIFACTS_DIR);
        if (artifactDir == null) {
            return null;
        }

        File paramFile = new File(artifactDir, Consts.PARAMS_YAML);
        if (paramFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            paramFile.delete();
        }
        try {
            FileUtils.writeStringToFile(paramFile, params, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Error with writing to params.yaml file", e);
            return null;
        }
        return paramFile;
    }

    public void processWorkbookStatus(String launchpadUrl, List<Protocol.WorkbookStatus.SimpleStatus> statuses) {
        currentExecState.register(launchpadUrl, statuses);
    }
}
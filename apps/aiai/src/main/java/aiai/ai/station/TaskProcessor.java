/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package aiai.ai.station;

import aiai.ai.Consts;
import aiai.ai.Globals;
import aiai.ai.comm.Protocol;
import aiai.ai.core.ExecProcessService;
import aiai.ai.resource.AssetFile;
import aiai.ai.resource.ResourceUtils;
import aiai.ai.station.env.EnvService;
import aiai.ai.station.sourcing.git.GitSourcingService;
import aiai.ai.station.station_resource.ResourceProvider;
import aiai.ai.station.station_resource.ResourceProviderFactory;
import aiai.ai.yaml.metadata.Metadata;
import aiai.ai.yaml.station_task.StationTask;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import aiai.api.v1.EnumsApi;
import aiai.api.v1.data.SnippetApiData;
import aiai.api.v1.data.TaskApiData;
import aiai.api.v1.data_storage.DataStorageParams;
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

    public void fixedDelay() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }

        List<StationTask> tasks = stationTaskService.findAllByFinishedOnIsNullAndAssetsPreparedIs(true);
        for (StationTask task : tasks) {
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

            final TaskApiData.TaskParamYaml taskParamYaml = TaskParamYamlUtils.toTaskYaml(task.getParams());

            StationService.ResultOfChecking resultOfChecking = stationService.checkForPreparingOfAssets(task, launchpadCode, taskParamYaml, launchpad, taskDir);
            if (resultOfChecking.isError) {
                continue;
            }
            boolean isAllLoaded = resultOfChecking.isAllLoaded;
            for (Map.Entry<String, List<AssetFile>> entry : resultOfChecking.assetFiles.entrySet()) {
                for (AssetFile assetFile : entry.getValue()) {
                    taskParamYaml.inputResourceAbsolutePaths
                            .computeIfAbsent(entry.getKey(), o-> new ArrayList<>())
                            .add(assetFile.file.getAbsolutePath());
                }
            }
            File outputResourceFile = stationService.getOutputResourceFile(task, taskParamYaml, launchpad, taskDir);
            if (outputResourceFile==null) {
                stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, "Broken task. Can't create outputResourceFile");
                continue;
            }
            taskParamYaml.outputResourceAbsolutePath = outputResourceFile.getAbsolutePath();
            if (taskParamYaml.snippet==null) {
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

            taskParamYaml.workingPath = taskDir.getAbsolutePath();
            final String params = TaskParamYamlUtils.toString(taskParamYaml);
            // persist params.yaml file
            final File paramFile = prepareParamFile(taskDir, params);
            if (paramFile == null) {
                log.warn("#100.20 param file wasn't created, task dir: {}" , taskDir.getAbsolutePath());
                continue;
            }

            // at this point all required resources have to be prepared

            task = stationTaskService.setLaunchOn(task.launchpadUrl, task.taskId);

            SnippetApiData.SnippetExecResult preSnippetExecResult = prepareAndExecSnippet(taskParamYaml.getPreSnippet(), task, launchpadCode, launchpad, taskDir, taskParamYaml, isAllLoaded, artifactDir, systemDir, paramFile);
            SnippetApiData.SnippetExecResult snippetExecResult = prepareAndExecSnippet(taskParamYaml.getSnippet(), task, launchpadCode, launchpad, taskDir, taskParamYaml, isAllLoaded, artifactDir, systemDir, paramFile);
            SnippetApiData.SnippetExecResult postSnippetExecResult = prepareAndExecSnippet(taskParamYaml.getPostSnippet(), task, launchpadCode, launchpad, taskDir, taskParamYaml, isAllLoaded, artifactDir, systemDir, paramFile);

            if (snippetExecResult == null) {
                continue;
            }

            stationTaskService.markAsFinished(task.launchpadUrl, task.getTaskId(), new SnippetApiData.SnippetExec(snippetExecResult, preSnippetExecResult, postSnippetExecResult));
        }
    }

    @SuppressWarnings("WeakerAccess")
    // TODO 2019.05.02 implement unit-test for this method
    public SnippetApiData.SnippetExecResult prepareAndExecSnippet(SnippetApiData.SnippetConfig snippet, StationTask task, Metadata.LaunchpadInfo launchpadCode, LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad, File taskDir, TaskApiData.TaskParamYaml taskParamYaml, boolean isAllLoaded, File artifactDir, File systemDir, File paramFile) {
        if (snippet==null) {
            return null;
        }
        SnippetPrepareResult snippetPrepareResult = prepareSnippet(launchpadCode, snippet);
        if (snippetPrepareResult == null) {
            return null;
        }

        if (!snippetPrepareResult.isLoaded || !isAllLoaded) {
            return null;
        }

        //noinspection UnnecessaryLocalVariable
        SnippetApiData.SnippetExecResult snippetExecResult = execSnippet(task, launchpadCode, launchpad, taskDir, taskParamYaml, artifactDir, systemDir, paramFile, snippetPrepareResult);
        return snippetExecResult;
    }

    @SuppressWarnings("WeakerAccess")
    // TODO 2019.05.02 implement unit-test for this method
    public SnippetApiData.SnippetExecResult execSnippet(StationTask task, Metadata.LaunchpadInfo launchpadCode, LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad, File taskDir, TaskApiData.TaskParamYaml taskParamYaml, File artifactDir, File systemDir, File paramFile, SnippetPrepareResult snippetPrepareResult) {
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

            long startedOn = System.currentTimeMillis();

            // Exec snippet
            snippetExecResult = execProcessService.execCommand(cmd, taskDir, consoleLogFile, taskParamYaml.timeoutBeforeTerminate);

            // Store result
            stationTaskService.storeExecResult(task.launchpadUrl, task.getTaskId(), startedOn, snippetPrepareResult.snippet, snippetExecResult, artifactDir);

            if (snippetExecResult.isOk()) {
                final DataStorageParams params = taskParamYaml.resourceStorageUrls.get(taskParamYaml.outputResourceCode);
                ResourceProvider resourceProvider = resourceProviderFactory.getResourceProvider(params.sourcing);
                SnippetApiData.SnippetExecResult tempSnippetExecResult = resourceProvider.processResultingFile(
                        launchpad, task, launchpadCode,
                        new File(taskParamYaml.outputResourceAbsolutePath)
                );
                if (tempSnippetExecResult !=null) {
                    snippetExecResult = tempSnippetExecResult;
                }
        }
        } catch (Throwable th) {
            log.error("Error exec process:\n" +
                    "\tenv: " + snippetPrepareResult.snippet.env +"\n" +
                    "\tinterpreter: " + interpreter+"\n" +
                    "\tfile: " + (snippetPrepareResult.snippetAssetFile!=null && snippetPrepareResult.snippetAssetFile.file!=null
                    ? snippetPrepareResult.snippetAssetFile.file.getAbsolutePath()
                    : snippetPrepareResult.snippet.file) +"\n" +
                    "\tparams", th);
            snippetExecResult = new SnippetApiData.SnippetExecResult(false, -1, ExceptionUtils.getStackTrace(th));
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
                return null;
            }
            snippetPrepareResult.snippetAssetFile = new AssetFile();
            snippetPrepareResult.snippetAssetFile.file = new File(result.snippetDir, snippetPrepareResult.snippet.file);
            log.info("Snippet asset file: {}, exist: {}", snippetPrepareResult.snippetAssetFile.file.getAbsolutePath(), snippetPrepareResult.snippetAssetFile.file.exists() );
        }
        return snippetPrepareResult;
    }

    @Data
    public static class SnippetPrepareResult {
        public SnippetApiData.SnippetConfig snippet;
        public AssetFile snippetAssetFile;
        boolean isLoaded = true;
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
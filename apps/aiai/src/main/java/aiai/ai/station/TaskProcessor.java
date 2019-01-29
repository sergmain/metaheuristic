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
import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.comm.Protocol;
import aiai.ai.core.ExecProcessService;
import aiai.ai.exceptions.ResourceProviderException;
import aiai.ai.resource.AssetFile;
import aiai.ai.resource.ResourceProvider;
import aiai.ai.resource.ResourceProviderFactory;
import aiai.ai.station.actors.UploadResourceActor;
import aiai.ai.station.tasks.UploadResourceTask;
import aiai.ai.utils.CollectionUtils;
import aiai.ai.resource.ResourceUtils;
import aiai.ai.yaml.metadata.Metadata;
import aiai.ai.yaml.station.StationTask;
import aiai.ai.yaml.task.SimpleSnippet;
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Profile("station")
public class TaskProcessor {

    private final Globals globals;

    private final ExecProcessService execProcessService;
    private final StationService stationService;
    private final TaskParamYamlUtils taskParamYamlUtils;
    private final StationTaskService stationTaskService;
    private final CurrentExecState currentExecState;
    private final UploadResourceActor uploadResourceActor;
    private final LaunchpadLookupExtendedService launchpadLookupExtendedService ;
    private final MetadataService metadataService;
    private final ResourceProviderFactory resourceProviderFactory;

    public TaskProcessor(Globals globals, ExecProcessService execProcessService, StationService stationService, TaskParamYamlUtils taskParamYamlUtils, StationTaskService stationTaskService, CurrentExecState currentExecState, UploadResourceActor uploadResourceActor, LaunchpadLookupExtendedService launchpadLookupExtendedService, MetadataService metadataService, ResourceProviderFactory resourceProviderFactory) {
        this.globals = globals;
        this.execProcessService = execProcessService;
        this.stationService = stationService;
        this.taskParamYamlUtils = taskParamYamlUtils;
        this.stationTaskService = stationTaskService;
        this.currentExecState = currentExecState;
        this.uploadResourceActor = uploadResourceActor;
        this.launchpadLookupExtendedService = launchpadLookupExtendedService;
        this.metadataService = metadataService;
        this.resourceProviderFactory = resourceProviderFactory;
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
                log.info("Can't process task for url {} at this time, time: {}, permitted period of time: {}", new Date(), launchpad.schedule.asString);
                return;
            }

            if (StringUtils.isBlank(task.getParams())) {
                log.warn("Params for task {} is blank", task.getTaskId());
                continue;
            }
            Enums.FlowInstanceExecState state = currentExecState.getState(task.launchpadUrl, task.flowInstanceId);
            if (state==Enums.FlowInstanceExecState.UNKNOWN) {
                log.info("The state for FlowInstance #{}, host {} is unknown, skip it", task.flowInstanceId, task.launchpadUrl);
                continue;
            }

            if (state!=Enums.FlowInstanceExecState.STARTED) {
                log.info("The state for FlowInstance #{}, host is {}, skip it", task.flowInstanceId, task.launchpadUrl, state);
                continue;
            }

            File taskDir = stationTaskService.prepareTaskDir(task.launchpadUrl, task.taskId);

            final TaskParamYaml taskParamYaml = taskParamYamlUtils.toTaskYaml(task.getParams());

            StationService.ResultOfChecking resultOfChecking = stationService.checkForPreparingOfAssets(task, launchpadCode, taskParamYaml, launchpad, taskDir);
            if (resultOfChecking.isError) {
                continue;
            }
            boolean isAllLoaded = resultOfChecking.isAllLoaded;


/*

            boolean isAllLoaded = true;
            boolean isError = false;
            try {
                for (String resourceCode : CollectionUtils.toPlainList(taskParamYaml.inputResourceCodes.values())) {
                    final String storageUrl = taskParamYaml.resourceStorageUrls.get(resourceCode);
                    if (storageUrl==null || storageUrl.isBlank()) {
                        stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, "Can't find storageUrl for resourceCode "+ resourceCode);
                        log.error("storageUrl wasn't found for resourceCode ", resourceCode);
                        isError = true;
                        break;
                    }
                    ResourceProvider resourceProvider = resourceProviderFactory.getResourceProvider(storageUrl);
                    List<AssetFile> assetFiles = resourceProvider.prepareDataFile(taskDir, launchpad, task, launchpadCode, resourceCode, storageUrl);
                    for (AssetFile assetFile : assetFiles) {
                        // is this resource prepared?
                        if (assetFile.isError || !assetFile.isContent) {
                            isAllLoaded=false;
                            break;
                        }
                    }
                }
            } catch (ResourceProviderException e) {
                log.error("Error", e);
                stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, e.toString());
                continue;
            }
            if (isError) {
                continue;
            }
            if (!isAllLoaded) {
                if (task.assetsPrepared) {
                    stationTaskService.markAsAssetPrepared(task.launchpadUrl, task.taskId, false);
                }
                continue;
            }
*/


/*
            for (String resourceCode : CollectionUtils.toPlainList(taskParamYaml.inputResourceCodes.values())) {
                String storageUrl = taskParamYaml.resourceStorageUrls.get(resourceCode);
                if (storageUrl==null) {
                    stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, "Can't find storageUrl for resourceCode "+ resourceCode);
                    isError = true;
                    break;

                }
                AssetFile assetFile = ResourceUtils.prepareDataFile(taskDir, resourceCode, null);
                // is this resource prepared?
                if (assetFile.isError || !assetFile.isContent) {
                    log.info("Resource hasn't been prepared yet, {}", assetFile);
                    isAllLoaded = false;
                }
            }
*/

            if (taskParamYaml.snippet==null) {
                stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, "Broken task. Snippet isn't defined");
                continue;
            }

            File artifactDir = stationTaskService.prepareTaskSubDir(taskDir, Consts.ARTIFACTS_DIR);
            if (artifactDir == null) {
                stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, "Error of configuring of environment. 'artifacts' directory wasn't created, task can't be processed.");
                continue;
            }

            // at this point all required resources have to be downloaded from server

            taskParamYaml.workingPath = taskDir.getAbsolutePath();
            final String params = taskParamYamlUtils.toString(taskParamYaml);

            task = stationTaskService.setLaunchOn(task.launchpadUrl, task.taskId);
            SimpleSnippet snippet = taskParamYaml.getSnippet();

            AssetFile snippetAssetFile=null;
            if (!snippet.fileProvided) {
                final File snippetDir = stationTaskService.prepareSnippetDir(launchpadCode);
                snippetAssetFile = ResourceUtils.prepareSnippetFile(snippetDir, snippet.code, snippet.filename);
                // is this snippet prepared?
                if (snippetAssetFile.isError || !snippetAssetFile.isContent) {
                    log.info("Resource hasn't been prepared yet, {}", snippetAssetFile);
                    isAllLoaded = false;
                }
            }
            if (!isAllLoaded) {
                continue;
            }

            final File paramFile = prepareParamFile(taskDir, params);
            if (paramFile == null) {
                break;
            }
            Interpreter interpreter = new Interpreter(stationService.getEnvYaml().getEnvs().get(snippet.env));
            if (interpreter.list == null) {
                log.warn("Can't process the task, the interpreter wasn't found for env: {}", snippet.env);
                break;
            }

            log.info("All systems are checked for the task #{}, lift off", task.taskId );

            ExecProcessService.Result result;
            try {
                List<String> cmd = Arrays.stream(interpreter.list).collect(Collectors.toList());

                // bug in IDEA with analyzing !snippet.fileProvided, so we have to add '&& snippetAssetFile!=null'
                if (!snippet.fileProvided && snippetAssetFile!=null) {
                    cmd.add(snippetAssetFile.file.getAbsolutePath());
                }
                if (StringUtils.isNoneBlank(snippet.params)) {
                    cmd.addAll(Arrays.stream(StringUtils.split(snippet.params)).collect(Collectors.toList()));
                }
                cmd.add( paramFile.getAbsolutePath() );

                File consoleLogFile = new File(artifactDir, Consts.SYSTEM_CONSOLE_OUTPUT_FILE_NAME);

                long startedOn = System.currentTimeMillis();

                // Exec snippet
                result = execProcessService.execCommand(cmd, taskDir, consoleLogFile);

                // Store result
                stationTaskService.storeExecResult(task.launchpadUrl, task.getTaskId(), startedOn, snippet, result, artifactDir);

                if (result.isOk()) {
                    File resultDataFile = new File(taskDir, Consts.ARTIFACTS_DIR + File.separatorChar + taskParamYaml.outputResourceCode);
                    if (resultDataFile.exists()) {
                        log.info("Register task for uploading result data to server, resultDataFile: {}", resultDataFile.getPath());
                        UploadResourceTask uploadResourceTask = new UploadResourceTask(task.taskId, resultDataFile);
                        uploadResourceTask.launchpad = launchpad.launchpadLookup;
                        uploadResourceTask.stationId = launchpadCode.stationId;
                        uploadResourceActor.add(uploadResourceTask);
                    } else {
                        String es = "Result data file doesn't exist, resultDataFile: " + resultDataFile.getPath();
                        log.error(es);
                        result = new ExecProcessService.Result(false, -1, es);
                    }
                }
            } catch (Throwable th) {
                log.error("Error exec process " + interpreter, th);
                result = new ExecProcessService.Result(false, -1, ExceptionUtils.getStackTrace(th));
            }
            stationTaskService.markAsFinished(task.launchpadUrl, task.getTaskId(), result);
        }
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

    public void processFlowInstanceStatus(String launchpadUrl, List<Protocol.FlowInstanceStatus.SimpleStatus> statuses) {
        currentExecState.register(launchpadUrl, statuses);
    }
}
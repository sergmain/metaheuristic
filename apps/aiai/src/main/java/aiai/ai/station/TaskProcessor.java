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
import aiai.ai.resource.AssetFile;
import aiai.ai.resource.ResourceUtils;
import aiai.ai.station.env.EnvService;
import aiai.ai.station.station_resource.ResourceProvider;
import aiai.ai.station.station_resource.ResourceProviderFactory;
import aiai.ai.yaml.metadata.Metadata;
import aiai.ai.yaml.station.StationTask;
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import aiai.api.v1.EnumsApi;
import aiai.apps.commons.yaml.snippet.SnippetConfig;
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

    public TaskProcessor(Globals globals, ExecProcessService execProcessService, StationTaskService stationTaskService, CurrentExecState currentExecState, LaunchpadLookupExtendedService launchpadLookupExtendedService, MetadataService metadataService, EnvService envService, StationService stationService, ResourceProviderFactory resourceProviderFactory) {
        this.globals = globals;
        this.execProcessService = execProcessService;
        this.stationTaskService = stationTaskService;
        this.currentExecState = currentExecState;
        this.launchpadLookupExtendedService = launchpadLookupExtendedService;
        this.metadataService = metadataService;
        this.envService = envService;
        this.stationService = stationService;
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
                log.info("Can't process task for url {} at this time, time: {}, permitted period of time: {}", task.launchpadUrl, new Date(), launchpad.schedule.asString);
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
                log.info("The state for FlowInstance #{}, host: {}, is {}, skip it", task.flowInstanceId, task.launchpadUrl, state);
                continue;
            }

            File taskDir = stationTaskService.prepareTaskDir(task.launchpadUrl, task.taskId);

            final TaskParamYaml taskParamYaml = TaskParamYamlUtils.toTaskYaml(task.getParams());

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

            // at this point all required resources have to be prepared

            taskParamYaml.workingPath = taskDir.getAbsolutePath();
            final String params = TaskParamYamlUtils.toString(taskParamYaml);

            task = stationTaskService.setLaunchOn(task.launchpadUrl, task.taskId);
            SnippetConfig snippet = taskParamYaml.getSnippet();

            AssetFile snippetAssetFile=null;
            if (snippet.sourcing== EnumsApi.SnippetSourcing.launchpad) {
                final File snippetDir = stationTaskService.prepareSnippetDir(launchpadCode);
                snippetAssetFile = ResourceUtils.prepareSnippetFile(snippetDir, snippet.getCode(), snippet.file);
                // is this snippet prepared?
                if (snippetAssetFile.isError || !snippetAssetFile.isContent) {
                    log.info("Resource hasn't been prepared yet, {}", snippetAssetFile);
                    isAllLoaded = false;
                }
            }

            if (!isAllLoaded) {
                continue;
            }

            // persist params.yaml file
            final File paramFile = prepareParamFile(taskDir, params);
            if (paramFile == null) {
                break;
            }
            Interpreter interpreter = new Interpreter(envService.getEnvYaml().getEnvs().get(snippet.env));
            if (interpreter.list == null) {
                log.warn("Can't process the task, the interpreter wasn't found for env: {}", snippet.env);
                break;
            }

            log.info("All systems are checked for the task #{}, lift off", task.taskId );

            ExecProcessService.Result result;
            try {
                List<String> cmd = Arrays.stream(interpreter.list).collect(Collectors.toList());

                if (snippet.sourcing== EnumsApi.SnippetSourcing.launchpad && snippetAssetFile!=null) {
                    cmd.add(snippetAssetFile.file.getAbsolutePath());
                }
                if (StringUtils.isNoneBlank(snippet.params)) {
                    cmd.addAll(Arrays.stream(StringUtils.split(snippet.params)).collect(Collectors.toList()));
                }
                cmd.add( paramFile.getAbsolutePath() );

                File consoleLogFile = new File(artifactDir, Consts.SYSTEM_CONSOLE_OUTPUT_FILE_NAME);

                long startedOn = System.currentTimeMillis();

                // Exec snippet
                result = execProcessService.execCommand(cmd, taskDir, consoleLogFile, taskParamYaml.timeoutBeforeTerminate);

                // Store result
                stationTaskService.storeExecResult(task.launchpadUrl, task.getTaskId(), startedOn, snippet, result, artifactDir);

                if (result.isOk()) {
                    final String storageUrl = taskParamYaml.resourceStorageUrls.get(taskParamYaml.outputResourceCode);
                    if (storageUrl == null || storageUrl.isBlank()) {
                        String es = "Result data file doesn't exist, resultDataFile: " + taskParamYaml.outputResourceAbsolutePath;
                        log.error(es);
                        result = new ExecProcessService.Result(false, -1, es);
                    }
                    else {
                        ResourceProvider resourceProvider = resourceProviderFactory.getResourceProvider(storageUrl);
                        ExecProcessService.Result tempResult = resourceProvider.processResultingFile(
                                launchpad, task, launchpadCode,
                                new File(taskParamYaml.outputResourceAbsolutePath)
                        );
                        if (tempResult!=null) {
                            result = tempResult;
                        }
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
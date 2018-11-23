/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.station;

import aiai.ai.Consts;
import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.comm.Protocol;
import aiai.ai.core.ExecProcessService;
import aiai.ai.snippet.SnippetUtils;
import aiai.ai.station.actors.UploadResourceActor;
import aiai.ai.station.tasks.UploadResourceTask;
import aiai.ai.utils.CollectionUtils;
import aiai.ai.yaml.task.SimpleSnippet;
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import aiai.ai.yaml.station.StationTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TaskProcessor {

    private final Globals globals;

    private final ExecProcessService execProcessService;
    private final StationService stationService;
    private final TaskParamYamlUtils taskParamYamlUtils;
    private final StationTaskService stationTaskService;
    private final CurrentExecState currentExecState;
    private final UploadResourceActor uploadResourceActor;

    private Map<Enums.BinaryDataType, Map<String, AssetFile>> resourceReadyMap = new HashMap<>();

    public TaskProcessor(Globals globals, ExecProcessService execProcessService, StationService stationService, TaskParamYamlUtils taskParamYamlUtils, StationTaskService stationTaskService, CurrentExecState currentExecState, UploadResourceActor uploadResourceActor) {
        this.globals = globals;
        this.execProcessService = execProcessService;
        this.stationService = stationService;
        this.taskParamYamlUtils = taskParamYamlUtils;
        this.stationTaskService = stationTaskService;
        this.currentExecState = currentExecState;
        this.uploadResourceActor = uploadResourceActor;
    }

    private AssetFile getResource(Enums.BinaryDataType binaryDataType, String id) {
        return resourceReadyMap.containsKey(binaryDataType) ? resourceReadyMap.get(binaryDataType).get(id) : null;
    }

    private void putResource(Enums.BinaryDataType binaryDataType, String id, AssetFile assetFile) {
        Map<String, AssetFile> map = resourceReadyMap.putIfAbsent(binaryDataType, new HashMap<>());
        if (map==null) {
            map = new HashMap<>();
            resourceReadyMap.put(binaryDataType, map);
        }
        map.put(id, assetFile);
    }

    public void fixedDelay() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }
        if (!globals.timePeriods.isCurrentTimeActive()) {
            return;
        }

        File snippetDir = SnippetUtils.checkEvironment(globals.stationDir);
        if (snippetDir == null) {
            return;
        }

        List<StationTask> tasks = stationTaskService.findAllByFinishedOnIsNull();
        for (StationTask task : tasks) {
            if (StringUtils.isBlank(task.getParams())) {
                log.warn("Params for task {} is blank", task.getTaskId());
                continue;
            }
            if (!currentExecState.isStarted(task.flowInstanceId)) {
                continue;
            }
            File taskDir = stationTaskService.prepareTaskDir(task.taskId);

            final TaskParamYaml taskParamYaml = taskParamYamlUtils.toTaskYaml(task.getParams());
            boolean isResourcesOk = true;
            for (String resourceCode : CollectionUtils.toPlainList(taskParamYaml.inputResourceCodes.values())) {
                AssetFile assetFile= getResource(Enums.BinaryDataType.DATA, resourceCode);
                if (assetFile == null) {
                    assetFile = StationResourceUtils.prepareResourceFile(taskDir, Enums.BinaryDataType.DATA, resourceCode, null);
                    // is this resource prepared?
                    if (assetFile.isError || !assetFile.isContent) {
                        log.info("Resource hasn't been prepared yet, {}", assetFile);
                        isResourcesOk = false;
                        continue;
                    }
                    putResource(Enums.BinaryDataType.DATA, resourceCode, assetFile);
                }
            }
            if (!isResourcesOk) {
                continue;
            }

            if (taskParamYaml.snippet==null) {
                stationTaskService.finishAndWriteToLog(task, "Broken task. Snippet isn't defined");
                continue;
            }

            File artifactDir = stationTaskService.prepareTaskSubDir(taskDir, "artifacts");
            if (artifactDir == null) {
                stationTaskService.finishAndWriteToLog(task, "Error of configuring of environment. 'artifacts' directory wasn't created, task can't be processed.");
                continue;
            }

            // at this point all required resources have to be downloaded from server

            taskParamYaml.workingPath = taskDir.getAbsolutePath();
            final String params = taskParamYamlUtils.toString(taskParamYaml);

            task.setLaunchedOn(System.currentTimeMillis());
            task = stationTaskService.save(task);
            SimpleSnippet snippet = taskParamYaml.getSnippet();

            AssetFile assetFile=null;
            if (!snippet.fileProvided) {
                assetFile = getResource(Enums.BinaryDataType.SNIPPET, snippet.code);
                if (assetFile == null) {
                    assetFile = StationResourceUtils.prepareResourceFile(globals.stationResourcesDir, Enums.BinaryDataType.SNIPPET, snippet.code, snippet.filename);
                    // is this snippet prepared?
                    if (assetFile.isError || !assetFile.isContent) {
                        log.info("Resource hasn't been prepared yet, {}", assetFile);
                        isResourcesOk = false;
                    } else {
                        putResource(Enums.BinaryDataType.SNIPPET, snippet.code, assetFile);
                    }
                }
                if (!isResourcesOk) {
                    continue;
                }
            }

            final File paramFile = prepareParamFile(taskDir, Consts.ARTIFACTS_DIR, params);
            if (paramFile == null) {
                break;
            }
            Interpreter interpreter = new Interpreter(stationService.getEnvYaml().getEnvs().get(snippet.env));
            if (interpreter.list == null) {
                log.warn("Can't process the task, the interpreter wasn't found for env: {}", snippet.env);
                break;
            }

            log.info("all system are checked, lift off");

            ExecProcessService.Result result = null;
            try {
                List<String> cmd = Arrays.stream(interpreter.list).collect(Collectors.toList());;
                if (!snippet.fileProvided && assetFile!=null) {
                    cmd.add(assetFile.file.getAbsolutePath());
                }
                if (StringUtils.isNoneBlank(snippet.params)) {
                    cmd.addAll(Arrays.stream(StringUtils.split(snippet.params)).collect(Collectors.toList()));
                }
                cmd.add(Consts.ARTIFACTS_DIR+File.separatorChar+Consts.PARAMS_YAML);
                result = execProcessService.execCommand(cmd, taskDir);
                stationTaskService.storeExecResult(task.getTaskId(), snippet, result, artifactDir);
                if (!result.isOk()) {
                    break;
                }

                File resultDataFile = new File(taskDir, Consts.ARTIFACTS_DIR+File.separatorChar+taskParamYaml.outputResourceCode);
                if (resultDataFile.exists()) {
                    log.info("Register task for uploading result data to server, resultDataFile: {}", resultDataFile.getPath());
                    uploadResourceActor.add(new UploadResourceTask(resultDataFile, task.taskId));
                }
                else {
                    log.error("Result data file doesn't exist, resultDataFile: {}", resultDataFile.getPath());
                }
            } catch (Exception err) {
                log.error("Error exec process " + interpreter, err);
            }
            stationTaskService.markAsFinishedIfAllOk(
                    task.getTaskId(), result!=null ? result.isOk : false,
                    result!=null ? result.exitCode : -1, result!=null ? result.console : "");
        }
    }

    private File prepareParamFile(File taskDir, String snippetType, String params) {
        File snippetTypeDir = stationTaskService.prepareTaskSubDir(taskDir, snippetType);
        if (snippetTypeDir == null) {
            return null;
        }

        File paramFile = new File(snippetTypeDir, Consts.PARAMS_YAML);
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

    public void processFlowInstanceStatus(List<Protocol.FlowInstanceStatus.SimpleStatus> statuses) {
        currentExecState.register(statuses);
    }
}
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
import aiai.ai.utils.DigitUtils;
import aiai.ai.yaml.sequence.SimpleSnippet;
import aiai.ai.yaml.sequence.TaskParamYaml;
import aiai.ai.yaml.sequence.TaskParamYamlUtils;
import aiai.ai.yaml.station.StationTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class TaskProcessor {

    private final Globals globals;

    private final ExecProcessService execProcessService;
    private final StationService stationService;
    private final TaskParamYamlUtils taskParamYamlUtils;
    private final StationTaskService stationTaskService;
    private final CurrentExecState currentExecState;

    private Map<Enums.BinaryDataType, Map<String, AssetFile>> resourceReadyMap = new HashMap<>();

    public TaskProcessor(Globals globals, ExecProcessService execProcessService, StationService stationService, TaskParamYamlUtils taskParamYamlUtils, StationTaskService stationTaskService, CurrentExecState currentExecState) {
        this.globals = globals;
        this.execProcessService = execProcessService;
        this.stationService = stationService;
        this.taskParamYamlUtils = taskParamYamlUtils;
        this.stationTaskService = stationTaskService;
        this.currentExecState = currentExecState;
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
            if (!currentExecState.isStarted(task.taskId)) {
                continue;
            }
            final TaskParamYaml taskParamYaml = taskParamYamlUtils.toTaskYaml(task.getParams());
            boolean isResourcesOk = true;
            for (String resourceCode : taskParamYaml.inputResourceCodes) {
                AssetFile assetFile= getResource(Enums.BinaryDataType.DATA, resourceCode);
                if (assetFile == null) {
                    assetFile = StationResourceUtils.prepareResourceFile(globals.stationResourcesDir, Enums.BinaryDataType.DATA, resourceCode);
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

            File taskDir = prepareTaskDir(task.taskId);

            File artifactDir = prepareTaskSubDir(taskDir, "artifacts");
            if (artifactDir == null) {
                stationTaskService.finishAndWriteToLog(task, "Error of configuring of environment. 'artifacts' directory wasn't created, task can't be processed.");
                continue;
            }

            // at this point all required resources have to be downloaded from server

            taskParamYaml.artifactPath = artifactDir.getAbsolutePath();
            final String params = taskParamYamlUtils.toString(taskParamYaml);

            task.setLaunchedOn(System.currentTimeMillis());
            task = stationTaskService.save(task);
            SimpleSnippet snippet = taskParamYaml.getSnippet();
            AssetFile assetFile= getResource(Enums.BinaryDataType.SNIPPET, snippet.code);
            if (assetFile == null) {
                assetFile = StationResourceUtils.prepareResourceFile(globals.stationResourcesDir, Enums.BinaryDataType.SNIPPET, snippet.code);
                // is this snippet prepared?
                if (assetFile.isError || !assetFile.isContent) {
                    log.info("Resource hasn't been prepared yet, {}", assetFile);
                    isResourcesOk = false;
                }
                else {
                    putResource(Enums.BinaryDataType.SNIPPET, snippet.code, assetFile);
                }
            }
            if (!isResourcesOk) {
                continue;
            }

/*
            SnippetExec snippetExec =  SnippetExecUtils.toSnippetExec(task.getSnippetExecResult());
            if (isThisSnippetCompletedWithError(snippet, snippetExec)) {
                // stop processing this task because last snippet was finished with an error
                break;
            }
            if (isThisSnippetCompleted(snippet, snippetExec)) {
                continue;
            }
*/

            final File paramFile = prepareParamFile(taskDir, snippet.getType(), params);
            if (paramFile == null) {
                break;
            }
            String interpreter = stationService.getEnvYaml().getEnvs().get(snippet.env);
            if (interpreter == null) {
                log.warn("Can't process sequence, interpreter wasn't found for env: {}", snippet.env);
                break;
            }

            log.info("all system are checked, lift off");

            try {
                List<String> cmd = new ArrayList<>();
                cmd.add(interpreter);
                cmd.add(assetFile.file.getAbsolutePath());

                final File execDir = paramFile.getParentFile();
                ExecProcessService.Result result = execProcessService.execCommand(cmd, execDir);
                stationTaskService.storeExecResult(task.getTaskId(), snippet, result, artifactDir);
                if (!result.isOk()) {
                    break;
                }

            } catch (Exception err) {
                log.error("Error exec process " + interpreter, err);
            }
            stationTaskService.markAsFinishedIfAllOk(task.getTaskId(), taskParamYaml);
        }
    }

    /*
    private boolean isThisSnippetCompleted(SimpleSnippet snippet, SnippetExec snippetExec) {
        if (snippetExec ==null) {
            return false;
        }
        return snippetExec.execs.get(snippet.order)!=null;
    }

    private boolean isThisSnippetCompletedWithError(SimpleSnippet snippet, SnippetExec snippetExec) {
        if (snippetExec ==null) {
            return false;
        }
        final ExecProcessService.Result result = snippetExec.execs.get(snippet.order);
        return result!=null && !result.isOk();
    }

*/
    private File prepareParamFile(File taskDir, String snippetType, String params) {
        File snippetTypeDir = prepareTaskSubDir(taskDir, snippetType);
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

    private File prepareTaskDir(Long taskId) {
        DigitUtils.Power power = DigitUtils.getPower(taskId);
        File taskDir = new File(globals.stationTaskDir,
                ""+power.power7+File.separatorChar+power.power4+File.separatorChar);
        taskDir.mkdirs();
        return taskDir;
    }

    private File prepareTaskSubDir(File taskDir, String snippetType) {
        File snippetTypeDir = new File(taskDir, snippetType);
        snippetTypeDir.mkdirs();
        if (!snippetTypeDir.exists()) {
            log.warn("Can't create snippetTypeDir: {}", snippetTypeDir.getAbsolutePath());
            return null;
        }
        return snippetTypeDir;
    }

    public void processExperimentStatus(List<Protocol.ExperimentStatus.SimpleStatus> statuses) {
        currentExecState.register(statuses);
    }
}
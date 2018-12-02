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
import aiai.ai.comm.Command;
import aiai.ai.comm.Protocol;
import aiai.ai.station.actors.UploadResourceActor;
import aiai.ai.station.tasks.UploadResourceTask;
import aiai.ai.yaml.env.EnvYaml;
import aiai.ai.yaml.env.EnvYamlUtils;
import aiai.ai.yaml.station.StationTask;
import aiai.ai.yaml.metadata.Metadata;
import aiai.ai.yaml.metadata.MetadataUtils;
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class StationService {

    private final Globals globals;
    private final StationTaskService stationTaskService;
    private final TaskParamYamlUtils taskParamYamlUtils;
    private final UploadResourceActor uploadResourceActor;

    private String env;
    private EnvYaml envYaml;
    private Metadata metadata = new Metadata();

    public StationService(Globals globals, StationTaskService stationTaskService, TaskParamYamlUtils taskParamYamlUtils, UploadResourceActor uploadResourceActor) {
        this.globals = globals;
        this.stationTaskService = stationTaskService;
        this.taskParamYamlUtils = taskParamYamlUtils;
        this.uploadResourceActor = uploadResourceActor;
    }

    @PostConstruct
    public void init() {
        if (!globals.isStationEnabled) {
            return;
        }

        final File file = new File(globals.stationDir, Consts.ENV_YAML_FILE_NAME);
        if (!file.exists()) {
            log.warn("Station's environment config file doesn't exist: {}", file.getPath());
            return;
        }
        try {
            env = FileUtils.readFileToString(file, Charsets.UTF_8);
            envYaml = EnvYamlUtils.toEnvYaml(env);
            if (envYaml==null) {
                log.error("env.yaml wasn't found or empty. path: {}{}env.yaml", globals.stationDir, File.separatorChar );
                throw new IllegalStateException("Station isn't configured, env.yaml is empty or doesn't exist");
            }
        } catch (IOException e) {
            log.error("Error", e);
            throw new IllegalStateException("Error while loading file: " + file.getPath(), e);
        }

        final File metadataFile = new File(globals.stationDir, Consts.METADATA_YAML_FILE_NAME);
        if (!metadataFile.exists()) {
            log.warn("Station's metadata file doesn't exist: {}", file.getPath());
            return;
        }
        try(FileInputStream fis = new FileInputStream(metadataFile)) {
            metadata = MetadataUtils.to(fis);
        } catch (IOException e) {
            log.error("Error", e);
            throw new IllegalStateException("Error while loading file: " + metadataFile.getPath(), e);
        }
        //noinspection unused
        int i=0;
    }

    public String getEnv() {
        return env;
    }

    EnvYaml getEnvYaml() {
        return envYaml;
    }

    Command produceReportStationStatus() {
        //noinspection UnnecessaryLocalVariable
        Protocol.ReportStationStatus reportStationStatus = new Protocol.ReportStationStatus(getEnv(), globals.stationActiveTime);
        return reportStationStatus;
    }

    private static final Object syncObj = new Object();

    public String getStationId() {
        synchronized (syncObj) {
            return metadata.metadata.get(StationConsts.STATION_ID);
        }
    }

    public void setStationId(String stationId) {
        if (StringUtils.isBlank(stationId)) {
            throw new IllegalStateException("StationId is null");
        }
        synchronized (syncObj) {
            metadata.metadata.put(StationConsts.STATION_ID, stationId);
            updateMetadataFile();
        }
    }

    private void updateMetadataFile() {
        final File metadataFile =  new File(globals.stationDir, Consts.METADATA_YAML_FILE_NAME);
        if (metadataFile.exists()) {
            log.info("Metadata file exists. Make backup");
            File yamlFileBak = new File(globals.stationDir, Consts.METADATA_YAML_FILE_NAME + ".bak");
            //noinspection ResultOfMethodCallIgnored
            yamlFileBak.delete();
            if (metadataFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                metadataFile.renameTo(yamlFileBak);
            }
        }

        try {
            FileUtils.write(metadataFile, MetadataUtils.toString(metadata), Charsets.UTF_8, false);
        } catch (IOException e) {
            log.error("Error", e);
            throw new IllegalStateException("Error while writing to file: " + metadataFile.getPath(), e);
        }
    }

    public void markAsDelivered(List<Long> ids) {
        for (Long id : ids) {
            stationTaskService.setDelivered(id);
        }
    }

    public void assignTasks(List<Protocol.AssignedTask.Task> tasks) {
        if (tasks==null || tasks.isEmpty()) {
            return;
        }
        synchronized (StationSyncHolder.stationGlobalSync) {
            for (Protocol.AssignedTask.Task task : tasks) {
                stationTaskService.createTask(task.taskId, task.flowInstanceId, task.params);
            }
        }
    }

    public Enums.ResendTaskOutputResourceStatus resendTaskOutputResource(long taskId) {
        File taskDir = stationTaskService.prepareTaskDir(taskId);
        File paramFile = new File(taskDir, Consts.ARTIFACTS_DIR+File.separatorChar+Consts.PARAMS_YAML);
        if (!paramFile.isFile() || !paramFile.exists()) {
            return Enums.ResendTaskOutputResourceStatus.TASK_IS_BROKEN;
        }
        String params;
        try {
            params = FileUtils.readFileToString(paramFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Error reading param file "+ paramFile.getPath(), e);
            return Enums.ResendTaskOutputResourceStatus.TASK_PARAM_FILE_NOT_FOUND;
        }
        final TaskParamYaml taskParamYaml = taskParamYamlUtils.toTaskYaml(params);
        final AssetFile assetFile = StationResourceUtils.prepareDataFile(taskDir, taskParamYaml.outputResourceCode, taskParamYaml.outputResourceCode);
        // is this resource prepared?
        if (assetFile.isError || !assetFile.isContent) {
            log.info("Resource hasn't been prepared yet, {}", assetFile);
            return Enums.ResendTaskOutputResourceStatus.RESOURCE_NOT_FOUND;
        }
        uploadResourceActor.add(new UploadResourceTask(taskId, assetFile.file));
        return Enums.ResendTaskOutputResourceStatus.SEND_SCHEDULED;
    }
}

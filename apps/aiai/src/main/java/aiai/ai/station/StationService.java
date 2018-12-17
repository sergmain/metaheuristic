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
import aiai.ai.yaml.env.TimePeriods;
import aiai.ai.yaml.launchpad_lookup.LaunchpadLookupConfig;
import aiai.ai.yaml.launchpad_lookup.LaunchpadLookupConfigUtils;
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class StationService {

    private final Globals globals;
    private final StationTaskService stationTaskService;
    private final TaskParamYamlUtils taskParamYamlUtils;
    private final UploadResourceActor uploadResourceActor;
    private final MetadataService metadataService;

    private String env;
    private EnvYaml envYaml;

    public  final Map<String, LaunchpadLookupExtended> lookupExtendedMap = new HashMap<>();

    public static class LaunchpadLookupExtended {
        public LaunchpadLookupConfig.LaunchpadLookup launchpadLookup;
        public TimePeriods periods;
    }

    public StationService(Globals globals, StationTaskService stationTaskService, TaskParamYamlUtils taskParamYamlUtils, UploadResourceActor uploadResourceActor, MetadataService metadataService) {
        this.globals = globals;
        this.stationTaskService = stationTaskService;
        this.taskParamYamlUtils = taskParamYamlUtils;
        this.uploadResourceActor = uploadResourceActor;
        this.metadataService = metadataService;
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
            envYaml = EnvYamlUtils.to(env);
            if (envYaml==null) {
                log.error("env.yaml wasn't found or empty. path: {}{}env.yaml", globals.stationDir, File.separatorChar );
                throw new IllegalStateException("Station isn't configured, env.yaml is empty or doesn't exist");
            }
        } catch (IOException e) {
            log.error("Error", e);
            throw new IllegalStateException("Error while loading file: " + file.getPath(), e);
        }

        final File launchpadFile = new File(globals.stationDir, Consts.LAUNCHPAD_YAML_FILE_NAME);
        if (!launchpadFile.exists()) {
            log.warn("Station's launchpad config file doesn't exist: {}", launchpadFile.getPath());
            return;
        }
        try {
            final String cfg = FileUtils.readFileToString(launchpadFile, Charsets.UTF_8);
            LaunchpadLookupConfig launchpadLookupConfig = LaunchpadLookupConfigUtils.to(cfg);;
            if (launchpadLookupConfig==null) {
                log.error("{} wasn't found or empty. path: {}{}{}",
                        Consts.LAUNCHPAD_YAML_FILE_NAME, globals.stationDir,
                        File.separatorChar, Consts.LAUNCHPAD_YAML_FILE_NAME );
                throw new IllegalStateException("Station isn't configured, launchpad.yaml is empty or doesn't exist");
            }
            for (LaunchpadLookupConfig.LaunchpadLookup launchpad : launchpadLookupConfig.launchpads) {
                LaunchpadLookupExtended lookupExtended = new LaunchpadLookupExtended();
                lookupExtended.launchpadLookup = launchpad;
                lookupExtended.periods = TimePeriods.from(launchpad.taskProcessingTime);
                lookupExtendedMap.put( launchpad.url, lookupExtended );

                metadataService.launchpadUrlAsCode(launchpad.url);
            }
        } catch (IOException e) {
            log.error("Error", e);
            throw new IllegalStateException("Error while loading file: " + launchpadFile.getPath(), e);
        }
    }

    public String getEnv() {
        return env;
    }

    EnvYaml getEnvYaml() {
        return envYaml;
    }

    Command produceReportStationStatus(TimePeriods periods) {
        //noinspection UnnecessaryLocalVariable
        Protocol.ReportStationStatus reportStationStatus = new Protocol.ReportStationStatus(getEnv(), periods.asString);
        return reportStationStatus;
    }

    public void markAsDelivered(String launchpadUrl, List<Long> ids) {
        for (Long id : ids) {
            stationTaskService.setDelivered(launchpadUrl, id);
        }
    }

    public void assignTasks(String launchpadUrl, List<Protocol.AssignedTask.Task> tasks) {
        if (tasks==null || tasks.isEmpty()) {
            return;
        }
        synchronized (StationSyncHolder.stationGlobalSync) {
            for (Protocol.AssignedTask.Task task : tasks) {
                stationTaskService.createTask(launchpadUrl, task.taskId, task.flowInstanceId, task.params);
            }
        }
    }

    public Enums.ResendTaskOutputResourceStatus resendTaskOutputResource(String launchpadUrl, long taskId) {
        if (launchpadUrl==null) {
            throw new IllegalStateException("launchpadUrl is null");
        }
        File taskDir = stationTaskService.prepareTaskDir(metadataService.launchpadUrlAsCode(launchpadUrl), taskId);
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
        final StationService.LaunchpadLookupExtended launchpad = lookupExtendedMap.get(launchpadUrl);

        UploadResourceTask uploadResourceTask = new UploadResourceTask(taskId, assetFile.file);
        uploadResourceTask.launchpad = launchpad.launchpadLookup;
        uploadResourceActor.add(uploadResourceTask);
        return Enums.ResendTaskOutputResourceStatus.SEND_SCHEDULED;
    }
}

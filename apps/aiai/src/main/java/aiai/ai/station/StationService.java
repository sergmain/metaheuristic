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
import aiai.ai.comm.Command;
import aiai.ai.comm.Protocol;
import aiai.ai.exceptions.ResourceProviderException;
import aiai.ai.resource.AssetFile;
import aiai.ai.resource.ResourceProvider;
import aiai.ai.resource.ResourceProviderFactory;
import aiai.ai.station.actors.UploadResourceActor;
import aiai.ai.station.tasks.UploadResourceTask;
import aiai.ai.resource.ResourceUtils;
import aiai.ai.utils.CollectionUtils;
import aiai.ai.yaml.env.EnvYaml;
import aiai.ai.yaml.env.EnvYamlUtils;
import aiai.ai.yaml.launchpad_lookup.LaunchpadSchedule;
import aiai.ai.yaml.metadata.Metadata;
import aiai.ai.yaml.station.StationTask;
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@Slf4j
@Profile("station")
public class StationService {

    private final Globals globals;
    private final StationTaskService stationTaskService;
    private final TaskParamYamlUtils taskParamYamlUtils;
    private final UploadResourceActor uploadResourceActor;
    private final MetadataService metadataService;
    private final LaunchpadLookupExtendedService launchpadLookupExtendedService;
    private final ResourceProviderFactory resourceProviderFactory;

    private String env;
    private EnvYaml envYaml;

    public StationService(Globals globals, StationTaskService stationTaskService, TaskParamYamlUtils taskParamYamlUtils, UploadResourceActor uploadResourceActor, MetadataService metadataService, LaunchpadLookupExtendedService launchpadLookupExtendedService, ResourceProviderFactory resourceProviderFactory) {
        this.globals = globals;
        this.stationTaskService = stationTaskService;
        this.taskParamYamlUtils = taskParamYamlUtils;
        this.uploadResourceActor = uploadResourceActor;
        this.metadataService = metadataService;
        this.launchpadLookupExtendedService = launchpadLookupExtendedService;
        this.resourceProviderFactory = resourceProviderFactory;
    }

    @PostConstruct
    public void init() {
        if (!globals.isStationEnabled) {
            return;
        }

        final File file = new File(globals.stationDir, Consts.ENV_YAML_FILE_NAME);
        if (!file.exists()) {
            log.warn("#747.01 Station's environment config file doesn't exist: {}", file.getPath());
            return;
        }
        try {
            env = FileUtils.readFileToString(file, Charsets.UTF_8);
            envYaml = EnvYamlUtils.to(env);
            if (envYaml==null) {
                log.error("#747.07 env.yaml wasn't found or empty. path: {}{}env.yaml", globals.stationDir, File.separatorChar );
                throw new IllegalStateException("Station isn't configured, env.yaml is empty or doesn't exist");
            }
        } catch (IOException e) {
            String es = "#747.11 Error while loading file: " + file.getPath();
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
    }

    public String getEnv() {
        return env;
    }

    public EnvYaml getEnvYaml() {
        return envYaml;
    }

    Command produceReportStationStatus(LaunchpadSchedule schedule) {
        //noinspection UnnecessaryLocalVariable
        Protocol.ReportStationStatus reportStationStatus = new Protocol.ReportStationStatus(getEnv(), schedule.asString);
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
            throw new IllegalStateException("#747.17 launchpadUrl is null");
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
            log.error("#747.23 Error reading param file "+ paramFile.getPath(), e);
            return Enums.ResendTaskOutputResourceStatus.TASK_PARAM_FILE_NOT_FOUND;
        }
        final TaskParamYaml taskParamYaml = taskParamYamlUtils.toTaskYaml(params);
        final AssetFile assetFile = ResourceUtils.prepareArtifactFile(taskDir, taskParamYaml.outputResourceCode, taskParamYaml.outputResourceCode);
        // is this resource prepared?
        if (assetFile.isError || !assetFile.isContent) {
            log.warn("#747.28 Resource hasn't been prepared yet, {}", assetFile);
            return Enums.ResendTaskOutputResourceStatus.RESOURCE_NOT_FOUND;
        }
        final Metadata.LaunchpadInfo launchpadCode = metadataService.launchpadUrlAsCode(launchpadUrl);
        final LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad =
                launchpadLookupExtendedService.lookupExtendedMap.get(launchpadUrl);

        UploadResourceTask uploadResourceTask = new UploadResourceTask(taskId, assetFile.file);
        uploadResourceTask.launchpad = launchpad.launchpadLookup;
        uploadResourceTask.stationId = launchpadCode.stationId;
        uploadResourceActor.add(uploadResourceTask);
        return Enums.ResendTaskOutputResourceStatus.SEND_SCHEDULED;
    }

    @Data
    public static class ResultOfChecking {
        public boolean isAllLoaded = true;
        public boolean isError = false;
    }

    public ResultOfChecking checkForPreparingOfAssets(StationTask task, Metadata.LaunchpadInfo launchpadCode, TaskParamYaml taskParamYaml, LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad, File taskDir) {
        ResultOfChecking result = new ResultOfChecking();
        try {
            for (String resourceCode : CollectionUtils.toPlainList(taskParamYaml.inputResourceCodes.values())) {
                final String storageUrl = taskParamYaml.resourceStorageUrls.get(resourceCode);
                if (storageUrl==null || storageUrl.isBlank()) {
                    stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, "Can't find storageUrl for resourceCode "+ resourceCode);
                    log.error("storageUrl wasn't found for resourceCode ", resourceCode);
                    result.isError = true;
                    break;
                }
                ResourceProvider resourceProvider = resourceProviderFactory.getResourceProvider(storageUrl);
                List<AssetFile> assetFiles = resourceProvider.prepareDataFile(taskDir, launchpad, task, launchpadCode, resourceCode, storageUrl);
                for (AssetFile assetFile : assetFiles) {
                    // is this resource prepared?
                    if (assetFile.isError || !assetFile.isContent) {
                        result.isAllLoaded=false;
                        break;
                    }
                }
            }
        } catch (ResourceProviderException e) {
            log.error("Error", e);
            stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, e.toString());
            result.isError = true;
            return result;
        }
        if (result.isError) {
            return result;
        }
        if (!result.isAllLoaded) {
            if (task.assetsPrepared) {
                stationTaskService.markAsAssetPrepared(task.launchpadUrl, task.taskId, false);
            }
            result.isError = true;
            return result;
        }
        result.isError = false;
        return result;
    }

}

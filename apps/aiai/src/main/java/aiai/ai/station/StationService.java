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
import aiai.ai.comm.Command;
import aiai.ai.comm.Protocol;
import aiai.ai.exceptions.ResourceProviderException;
import aiai.ai.resource.*;
import aiai.ai.station.actors.UploadResourceActor;
import aiai.ai.station.tasks.UploadResourceTask;
import aiai.ai.yaml.launchpad_lookup.LaunchpadSchedule;
import aiai.ai.yaml.metadata.Metadata;
import aiai.ai.yaml.station.StationTask;
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Profile;
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
@Profile("station")
public class StationService {

    private final StationTaskService stationTaskService;
    private final UploadResourceActor uploadResourceActor;
    private final MetadataService metadataService;
    private final LaunchpadLookupExtendedService launchpadLookupExtendedService;
    private final EnvService envService;
    private final ResourceProviderFactory resourceProviderFactory;

    public StationService(StationTaskService stationTaskService, UploadResourceActor uploadResourceActor, MetadataService metadataService, LaunchpadLookupExtendedService launchpadLookupExtendedService, EnvService envService, ResourceProviderFactory resourceProviderFactory) {
        this.stationTaskService = stationTaskService;
        this.uploadResourceActor = uploadResourceActor;
        this.metadataService = metadataService;
        this.launchpadLookupExtendedService = launchpadLookupExtendedService;
        this.envService = envService;
        this.resourceProviderFactory = resourceProviderFactory;
    }

    Command produceReportStationStatus(LaunchpadSchedule schedule) {
        //noinspection UnnecessaryLocalVariable
        Protocol.ReportStationStatus reportStationStatus = new Protocol.ReportStationStatus(envService.getEnv(), schedule.asString);
        return reportStationStatus;
    }

    /**
     * mark tasks as delivered.
     * By delivering it means that result of exec was delivered to launchpad
     *
     * @param launchpadUrl String
     * @param ids List&lt;String> list if task ids
     */
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
            throw new IllegalStateException("#747.07 launchpadUrl is null");
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
            log.error("#747.13 Error reading param file "+ paramFile.getPath(), e);
            return Enums.ResendTaskOutputResourceStatus.TASK_PARAM_FILE_NOT_FOUND;
        }
        final TaskParamYaml taskParamYaml = TaskParamYamlUtils.toTaskYaml(params);
        final String storageUrl = taskParamYaml.resourceStorageUrls.get(taskParamYaml.outputResourceCode);
        ResourceProvider resourceProvider = null;
        if (storageUrl == null || storageUrl.isBlank()) {
            log.error("##747.19 storageUrl wasn't found for outputResourceCode ", taskParamYaml.outputResourceCode);
            return Enums.ResendTaskOutputResourceStatus.TASK_IS_BROKEN;
        }
        else {
            try {
                resourceProvider = resourceProviderFactory.getResourceProvider(storageUrl);
            } catch (ResourceProviderException e) {
                log.error("#747.23 storageUrl wasn't found for outputResourceCode ", taskParamYaml.outputResourceCode);
                return Enums.ResendTaskOutputResourceStatus.TASK_IS_BROKEN;
            }
        }
        if (resourceProvider instanceof DiskResourceProvider) {
            return Enums.ResendTaskOutputResourceStatus.OUTPUT_RESOURCE_ON_EXTERNAL_STORAGE;
        }

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
        public Map<String, List<AssetFile>> assetFiles = new HashMap<>();
    }

    public StationService.ResultOfChecking checkForPreparingOfAssets(StationTask task, Metadata.LaunchpadInfo launchpadCode, TaskParamYaml taskParamYaml, LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad, File taskDir) {
        StationService.ResultOfChecking result = new StationService.ResultOfChecking();
        try {
            taskParamYaml.inputResourceCodes.forEach((key, value) -> {
                for (String resourceCode : value) {
                    final String storageUrl = taskParamYaml.resourceStorageUrls.get(resourceCode);
                    if (storageUrl == null || storageUrl.isBlank()) {
                        stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, "Can't find storageUrl for resourceCode " + resourceCode);
                        log.error("#747.34 storageUrl wasn't found for resourceCode ", resourceCode);
                        result.isError = true;
                        break;
                    }
                    ResourceProvider resourceProvider = resourceProviderFactory.getResourceProvider(storageUrl);
                    List<AssetFile> assetFiles = resourceProvider.prepareForDownloadingDataFile(taskDir, launchpad, task, launchpadCode, resourceCode, storageUrl);
                    result.assetFiles.computeIfAbsent(key, o -> new ArrayList<>()).addAll(assetFiles);
                    for (AssetFile assetFile : assetFiles) {
                        // is this resource prepared?
                        if (assetFile.isError || !assetFile.isContent) {
                            result.isAllLoaded = false;
                            break;
                        }
                    }
                }
            });
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

    public File getOutputResourceFile(StationTask task, TaskParamYaml taskParamYaml, LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad, File taskDir) {
        try {
            final String storageUrl = taskParamYaml.resourceStorageUrls.get(taskParamYaml.outputResourceCode);
            if (storageUrl == null || storageUrl.isBlank()) {
                stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, "Can't find storageUrl for resourceCode " + taskParamYaml.outputResourceCode);
                log.error("#747.39 storageUrl wasn't found for resourceCode ", taskParamYaml.outputResourceCode);
                return null;
            }

            ResourceProvider resourceProvider = resourceProviderFactory.getResourceProvider(storageUrl);
            //noinspection UnnecessaryLocalVariable
            File outputResourceFile = resourceProvider.getOutputResourceFile(
                    taskDir, launchpad, task, taskParamYaml.outputResourceCode, storageUrl);
            return outputResourceFile;
        } catch (ResourceProviderException e) {
            log.error("#747.42 Error", e);
            stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, e.toString());
            return null;
        }
    }

}

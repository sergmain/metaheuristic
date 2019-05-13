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
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.comm.Command;
import ai.metaheuristic.ai.comm.Protocol;
import ai.metaheuristic.ai.exceptions.ResourceProviderException;
import aiai.ai.resource.*;
import ai.metaheuristic.ai.station.actors.UploadResourceActor;
import ai.metaheuristic.ai.station.env.EnvService;
import ai.metaheuristic.ai.station.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.station.station_resource.DiskResourceProvider;
import ai.metaheuristic.ai.station.station_resource.ResourceProvider;
import ai.metaheuristic.ai.station.station_resource.ResourceProviderFactory;
import ai.metaheuristic.ai.station.tasks.UploadResourceTask;
import ai.metaheuristic.ai.yaml.launchpad_lookup.LaunchpadSchedule;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.station_task.StationTask;
import ai.metaheuristic.ai.yaml.task.TaskParamYamlUtils;
import ai.metaheuristic.api.v1.data.TaskApiData;
import ai.metaheuristic.api.v1.data_storage.DataStorageParams;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import ai.metaheuristic.ai.resource.AssetFile;
import ai.metaheuristic.ai.resource.ResourceUtils;
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
    private final GitSourcingService gitSourcingService;

    public StationService(StationTaskService stationTaskService, UploadResourceActor uploadResourceActor, MetadataService metadataService, LaunchpadLookupExtendedService launchpadLookupExtendedService, EnvService envService, ResourceProviderFactory resourceProviderFactory, GitSourcingService gitSourcingService) {
        this.stationTaskService = stationTaskService;
        this.uploadResourceActor = uploadResourceActor;
        this.metadataService = metadataService;
        this.launchpadLookupExtendedService = launchpadLookupExtendedService;
        this.envService = envService;
        this.resourceProviderFactory = resourceProviderFactory;
        this.gitSourcingService = gitSourcingService;
    }

    Command produceReportStationStatus(LaunchpadSchedule schedule) {
        //noinspection UnnecessaryLocalVariable
        Protocol.ReportStationStatus reportStationStatus = new Protocol.ReportStationStatus(
                envService.getEnvYaml(), schedule.asString, gitSourcingService.gitStatusInfo);
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
                stationTaskService.createTask(launchpadUrl, task.taskId, task.workbookId, task.params);
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
        final TaskApiData.TaskParamYaml taskParamYaml = TaskParamYamlUtils.toTaskYaml(params);
        final DataStorageParams dataStorageParams = taskParamYaml.resourceStorageUrls.get(taskParamYaml.outputResourceCode);
        ResourceProvider resourceProvider;
        try {
            resourceProvider = resourceProviderFactory.getResourceProvider(dataStorageParams.sourcing);
        } catch (ResourceProviderException e) {
            log.error("#747.23 storageUrl wasn't found for outputResourceCode {}", taskParamYaml.outputResourceCode);
            return Enums.ResendTaskOutputResourceStatus.TASK_IS_BROKEN;
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

    public StationService.ResultOfChecking checkForPreparingOfAssets(StationTask task, Metadata.LaunchpadInfo launchpadCode, TaskApiData.TaskParamYaml taskParamYaml, LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad, File taskDir) {
        StationService.ResultOfChecking result = new StationService.ResultOfChecking();
        try {
            taskParamYaml.inputResourceCodes.forEach((key, value) -> {
                for (String resourceCode : value) {
                    final DataStorageParams params = taskParamYaml.resourceStorageUrls.get(resourceCode);
                    ResourceProvider resourceProvider = resourceProviderFactory.getResourceProvider(params.sourcing);
                    List<AssetFile> assetFiles = resourceProvider.prepareForDownloadingDataFile(taskDir, launchpad, task, launchpadCode, resourceCode, params);
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

    public File getOutputResourceFile(StationTask task, TaskApiData.TaskParamYaml taskParamYaml, LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad, File taskDir) {
        try {
            final DataStorageParams dataStorageParams = taskParamYaml.resourceStorageUrls.get(taskParamYaml.outputResourceCode);

            ResourceProvider resourceProvider = resourceProviderFactory.getResourceProvider(dataStorageParams.sourcing);
            //noinspection UnnecessaryLocalVariable
            File outputResourceFile = resourceProvider.getOutputResourceFile(
                    taskDir, launchpad, task, taskParamYaml.outputResourceCode, dataStorageParams);
            return outputResourceFile;
        } catch (ResourceProviderException e) {
            final String msg = "#747.42 Error: " + e.toString();
            log.error(msg, e);
            stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, msg);
            return null;
        }
    }

}

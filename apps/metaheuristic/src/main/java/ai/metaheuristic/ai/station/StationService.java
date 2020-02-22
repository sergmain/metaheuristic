/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.exceptions.BreakFromForEachException;
import ai.metaheuristic.ai.exceptions.ResourceProviderException;
import ai.metaheuristic.ai.resource.AssetFile;
import ai.metaheuristic.ai.resource.ResourceUtils;
import ai.metaheuristic.ai.station.actors.UploadResourceActor;
import ai.metaheuristic.ai.station.env.EnvService;
import ai.metaheuristic.ai.station.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.station.station_resource.DiskResourceProvider;
import ai.metaheuristic.ai.station.station_resource.ResourceProvider;
import ai.metaheuristic.ai.station.station_resource.ResourceProviderFactory;
import ai.metaheuristic.ai.station.tasks.UploadResourceTask;
import ai.metaheuristic.ai.yaml.communication.mh.dispatcher..DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYaml;
import ai.metaheuristic.ai.yaml.mh.dispatcher._lookup.LaunchpadSchedule;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.station_task.StationTask;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@Profile("station")
@RequiredArgsConstructor
public class StationService {

    private final Globals globals;
    private final StationTaskService stationTaskService;
    private final UploadResourceActor uploadResourceActor;
    private final MetadataService metadataService;
    private final DispatcherLookupExtendedService mh.dispatcher.LookupExtendedService;
    private final EnvService envService;
    private final ResourceProviderFactory resourceProviderFactory;
    private final GitSourcingService gitSourcingService;

    StationCommParamsYaml.ReportStationStatus produceReportStationStatus(String mh.dispatcher.Url, LaunchpadSchedule schedule) {

        // TODO 2019-06-22 why sessionCreatedOn is System.currentTimeMillis()?
        // TODO 2019-08-29 why not? do we have to use a different type?
        StationCommParamsYaml.ReportStationStatus status = new StationCommParamsYaml.ReportStationStatus(
                envService.getEnvYaml(),
                gitSourcingService.gitStatusInfo,
                schedule.asString,
                metadataService.getSessionId(mh.dispatcher.Url),
                System.currentTimeMillis(),
                "[unknown]", "[unknown]", null, globals.logFile!=null && globals.logFile.exists(),
                TaskParamsYamlUtils.BASE_YAML_UTILS.getDefault().getVersion(),
                globals.os);

        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
            status.ip = inetAddress.getHostAddress();
            status.host = inetAddress.getHostName();
        } catch (UnknownHostException e) {
            log.error("#749.010 Error", e);
            status.addError(ExceptionUtils.getStackTrace(e));
        }

        return status;
    }

    /**
     * mark tasks as delivered.
     * By delivering it means that result of exec was delivered to mh.dispatcher.
     *
     * @param mh.dispatcher.Url String
     * @param ids List&lt;String> list if task ids
     */
    public void markAsDelivered(String mh.dispatcher.Url, List<Long> ids) {
        for (Long id : ids) {
            stationTaskService.setDelivered(mh.dispatcher.Url, id);
        }
    }

    public void assignTasks(String mh.dispatcher.Url, DispatcherCommParamsYaml.AssignedTask task) {
        if (task==null) {
            return;
        }
        synchronized (StationSyncHolder.stationGlobalSync) {
            stationTaskService.createTask(mh.dispatcher.Url, task.taskId, task.execContextId, task.params);
        }
    }

    public Enums.ResendTaskOutputResourceStatus resendTaskOutputResource(String mh.dispatcher.Url, long taskId) {
        if (mh.dispatcher.Url==null) {
            throw new IllegalStateException("#749.020 mh.dispatcher.Url is null");
        }
        StationTask task = stationTaskService.findById(mh.dispatcher.Url, taskId);
        if (task==null) {
            return Enums.ResendTaskOutputResourceStatus.TASK_NOT_FOUND;
        }
        final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
        File taskDir = stationTaskService.prepareTaskDir(metadataService.mh.dispatcher.UrlAsCode(mh.dispatcher.Url), taskId);

        final SourceCodeParamsYaml.Variable dataStorageParams = taskParamYaml.taskYaml.resourceStorageUrls
                .get(taskParamYaml.taskYaml.outputResourceIds.values().iterator().next());
        ResourceProvider resourceProvider;
        try {
            resourceProvider = resourceProviderFactory.getResourceProvider(dataStorageParams.sourcing);
        } catch (ResourceProviderException e) {
            log.error("#749.030 storageUrl wasn't found for outputResourceCode {}",
                    taskParamYaml.taskYaml.outputResourceIds.values().iterator().next());
            return Enums.ResendTaskOutputResourceStatus.TASK_IS_BROKEN;
        }
        if (resourceProvider instanceof DiskResourceProvider) {
            return Enums.ResendTaskOutputResourceStatus.OUTPUT_RESOURCE_ON_EXTERNAL_STORAGE;
        }

        final AssetFile assetFile = ResourceUtils.prepareOutputAssetFile(
                taskDir, taskParamYaml.taskYaml.outputResourceIds.values().iterator().next(),
                taskParamYaml.taskYaml.outputResourceIds.values().iterator().next());

        // is this resource prepared?
        if (assetFile.isError || !assetFile.isContent) {
            log.warn("#749.040 Resource wasn't found. Considering that this task is broken, {}", assetFile);
            stationTaskService.markAsFinishedWithError(task.mh.dispatcher.Url, task.taskId,
                    "#749.050 Resource wasn't found. Considering that this task is broken");
            stationTaskService.setCompleted(task.mh.dispatcher.Url, task.taskId);
            return Enums.ResendTaskOutputResourceStatus.RESOURCE_NOT_FOUND;
        }
        final Metadata.DispatcherInfo mh.dispatcher.Code = metadataService.mh.dispatcher.UrlAsCode(mh.dispatcher.Url);
        final DispatcherLookupExtendedService.DispatcherLookupExtended mh.dispatcher. =
                mh.dispatcher.LookupExtendedService.lookupExtendedMap.get(mh.dispatcher.Url);

        UploadResourceTask uploadResourceTask = new UploadResourceTask(taskId, assetFile.file);
        uploadResourceTask.mh.dispatcher. = mh.dispatcher..mh.dispatcher.Lookup;
        uploadResourceTask.stationId = mh.dispatcher.Code.stationId;
        uploadResourceActor.add(uploadResourceTask);
        return Enums.ResendTaskOutputResourceStatus.SEND_SCHEDULED;
    }

    @Data
    public static class ResultOfChecking {
        public boolean isAllLoaded = true;
        public boolean isError = false;
        public Map<String, List<AssetFile>> assetFiles = new HashMap<>();
    }

    public StationService.ResultOfChecking checkForPreparingOfAssets(StationTask task, Metadata.DispatcherInfo mh.dispatcher.Code, TaskParamsYaml taskParamYaml, DispatcherLookupExtendedService.DispatcherLookupExtended mh.dispatcher., File taskDir) {
        StationService.ResultOfChecking result = new StationService.ResultOfChecking();
        try {
            taskParamYaml.taskYaml.inputResourceIds.forEach((key, value) -> {
                for (String resourceCode : value) {
                    final SourceCodeParamsYaml.Variable params = taskParamYaml.taskYaml.resourceStorageUrls.get(resourceCode);
                    if (params==null) {
                        final String es = "#749.060 resource code: " + resourceCode + ", inconsistent taskParamsYaml:\n" + TaskParamsYamlUtils.BASE_YAML_UTILS.toString(taskParamYaml);
                        log.error(es);
                        throw new BreakFromForEachException(es);
                    }
                    ResourceProvider resourceProvider = resourceProviderFactory.getResourceProvider(params.sourcing);
                    List<AssetFile> assetFiles = resourceProvider.prepareForDownloadingDataFile(taskDir, mh.dispatcher., task, mh.dispatcher.Code, resourceCode, params);
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
        }
        catch (BreakFromForEachException e) {
            stationTaskService.markAsFinishedWithError(task.mh.dispatcher.Url, task.taskId, e.getMessage());
            result.isError = true;
            return result;
        }
        catch (ResourceProviderException e) {
            log.error("#749.070 Error", e);
            stationTaskService.markAsFinishedWithError(task.mh.dispatcher.Url, task.taskId, e.toString());
            result.isError = true;
            return result;
        }
        if (result.isError) {
            return result;
        }
        if (!result.isAllLoaded) {
            if (task.assetsPrepared) {
                stationTaskService.markAsAssetPrepared(task.mh.dispatcher.Url, task.taskId, false);
            }
            result.isError = true;
            return result;
        }
        result.isError = false;
        return result;
    }

    public File getOutputResourceFile(StationTask task, TaskParamsYaml taskParamYaml, DispatcherLookupExtendedService.DispatcherLookupExtended mh.dispatcher., File taskDir) {
        try {
            final SourceCodeParamsYaml.Variable dataStorageParams = taskParamYaml.taskYaml.resourceStorageUrls
                    .get(taskParamYaml.taskYaml.outputResourceIds.values().iterator().next());

            ResourceProvider resourceProvider = resourceProviderFactory.getResourceProvider(dataStorageParams.sourcing);
            //noinspection UnnecessaryLocalVariable
            File outputResourceFile = resourceProvider.getOutputResourceFile(
                    taskDir, mh.dispatcher., task, taskParamYaml.taskYaml.outputResourceIds.values().iterator().next(), dataStorageParams);
            return outputResourceFile;
        } catch (ResourceProviderException e) {
            final String msg = "#749.080 Error: " + e.toString();
            log.error(msg, e);
            stationTaskService.markAsFinishedWithError(task.mh.dispatcher.Url, task.taskId, msg);
            return null;
        }
    }
}

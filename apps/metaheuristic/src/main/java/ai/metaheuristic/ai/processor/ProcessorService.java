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
package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.exceptions.BreakFromForEachException;
import ai.metaheuristic.ai.exceptions.ResourceProviderException;
import ai.metaheuristic.ai.resource.AssetFile;
import ai.metaheuristic.ai.resource.ResourceUtils;
import ai.metaheuristic.ai.processor.actors.UploadResourceActor;
import ai.metaheuristic.ai.processor.env.EnvService;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.processor.processor_resource.DiskResourceProvider;
import ai.metaheuristic.ai.processor.processor_resource.ResourceProvider;
import ai.metaheuristic.ai.processor.processor_resource.ResourceProviderFactory;
import ai.metaheuristic.ai.processor.tasks.UploadResourceTask;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherSchedule;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorTask;
import ai.metaheuristic.api.EnumsApi;
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
@Profile("processor")
@RequiredArgsConstructor
public class ProcessorService {

    private final Globals globals;
    private final ProcessorTaskService processorTaskService;
    private final UploadResourceActor uploadResourceActor;
    private final MetadataService metadataService;
    private final DispatcherLookupExtendedService dispatcherLookupExtendedService;
    private final EnvService envService;
    private final ResourceProviderFactory resourceProviderFactory;
    private final GitSourcingService gitSourcingService;

    ProcessorCommParamsYaml.ReportProcessorStatus produceReportProcessorStatus(String dispatcherUrl, DispatcherSchedule schedule) {

        // TODO 2019-06-22 why sessionCreatedOn is System.currentTimeMillis()?
        // TODO 2019-08-29 why not? do we have to use a different type?
        ProcessorCommParamsYaml.ReportProcessorStatus status = new ProcessorCommParamsYaml.ReportProcessorStatus(
                envService.getEnvYaml(),
                gitSourcingService.gitStatusInfo,
                schedule.asString,
                metadataService.getSessionId(dispatcherUrl),
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
     * By delivering it means that result of exec was delivered to dispatcher
     *
     * @param dispatcherUrl String
     * @param ids List&lt;String> list if task ids
     */
    public void markAsDelivered(String dispatcherUrl, List<Long> ids) {
        for (Long id : ids) {
            processorTaskService.setDelivered(dispatcherUrl, id);
        }
    }

    public void assignTasks(String dispatcherUrl, DispatcherCommParamsYaml.AssignedTask task) {
        if (task==null) {
            return;
        }
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            processorTaskService.createTask(dispatcherUrl, task.taskId, task.execContextId, task.params);
        }
    }

    public Enums.ResendTaskOutputResourceStatus resendTaskOutputResources(String dispatcherUrl, long taskId) {
        if (dispatcherUrl==null) {
            throw new IllegalStateException("#749.020 dispatcherUrl is null");
        }
        ProcessorTask task = processorTaskService.findById(dispatcherUrl, taskId);
        if (task==null) {
            return Enums.ResendTaskOutputResourceStatus.TASK_NOT_FOUND;
        }
        final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
        File taskDir = processorTaskService.prepareTaskDir(metadataService.dispatcherUrlAsCode(dispatcherUrl), taskId);

        for (TaskParamsYaml.OutputVariable outputVariable : taskParamYaml.taskYaml.output) {
            Enums.ResendTaskOutputResourceStatus status = scheduleSendingOutputVariable(taskDir, outputVariable);
            if ()
        }
        return Enums.ResendTaskOutputResourceStatus.SEND_SCHEDULED;
    }

    private Enums.ResendTaskOutputResourceStatus scheduleSendingOutputVariable(File taskDir, TaskParamsYaml.OutputVariable outputVariable) {
        ResourceProvider resourceProvider;
        try {
            resourceProvider = resourceProviderFactory.getResourceProvider(outputVariable.sourcing);
        } catch (ResourceProviderException e) {
            log.error("#749.030 storageUrl wasn't found for outputResource {}", outputVariable.name);
            return Enums.ResendTaskOutputResourceStatus.TASK_IS_BROKEN;
        }
        if (resourceProvider instanceof DiskResourceProvider) {
            return Enums.ResendTaskOutputResourceStatus.SEND_SCHEDULED;
        }

        final AssetFile assetFile = ResourceUtils.prepareOutputAssetFile(
                taskDir, taskParamYaml.taskYaml.outputResourceIds.values().iterator().next(),
                taskParamYaml.taskYaml.outputResourceIds.values().iterator().next());

        // is this resource prepared?
        if (assetFile.isError || !assetFile.isContent) {
            log.warn("#749.040 Resource wasn't found. Considering that this task is broken, {}", assetFile);
            processorTaskService.markAsFinishedWithError(task.dispatcherUrl, task.taskId,
                    "#749.050 Resource wasn't found. Considering that this task is broken");
            processorTaskService.setCompleted(task.dispatcherUrl, task.taskId);
            return Enums.ResendTaskOutputResourceStatus.RESOURCE_NOT_FOUND;
        }
        final Metadata.DispatcherInfo dispatcherCode = metadataService.dispatcherUrlAsCode(dispatcherUrl);
        final DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher =
                dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);

        UploadResourceTask uploadResourceTask = new UploadResourceTask(taskId, assetFile.file);
        uploadResourceTask.dispatcher = dispatcher.dispatcherLookup;
        uploadResourceTask.processorId = dispatcherCode.processorId;
        uploadResourceActor.add(uploadResourceTask);

        return Enums.ResendTaskOutputResourceStatus.SEND_SCHEDULED;
    }


    @Data
    public static class ResultOfChecking {
        public boolean isAllLoaded = true;
        public boolean isError = false;
        public Map<String, List<AssetFile>> assetFiles = new HashMap<>();
    }

    public ProcessorService.ResultOfChecking checkForPreparingOfAssets(ProcessorTask task, Metadata.DispatcherInfo dispatcherCode, TaskParamsYaml taskParamYaml, DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher, File taskDir) {
        ProcessorService.ResultOfChecking result = new ProcessorService.ResultOfChecking();
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
                    List<AssetFile> assetFiles = resourceProvider.prepareForDownloadingDataFile(taskDir, dispatcher, task, dispatcherCode, resourceCode, params);
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
            processorTaskService.markAsFinishedWithError(task.dispatcherUrl, task.taskId, e.getMessage());
            result.isError = true;
            return result;
        }
        catch (ResourceProviderException e) {
            log.error("#749.070 Error", e);
            processorTaskService.markAsFinishedWithError(task.dispatcherUrl, task.taskId, e.toString());
            result.isError = true;
            return result;
        }
        if (result.isError) {
            return result;
        }
        if (!result.isAllLoaded) {
            if (task.assetsPrepared) {
                processorTaskService.markAsAssetPrepared(task.dispatcherUrl, task.taskId, false);
            }
            result.isError = true;
            return result;
        }
        result.isError = false;
        return result;
    }

    public File getOutputResourceFile(ProcessorTask task, TaskParamsYaml taskParamYaml, DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher, File taskDir) {
        try {
            final SourceCodeParamsYaml.Variable dataStorageParams = taskParamYaml.taskYaml.resourceStorageUrls
                    .get(taskParamYaml.taskYaml.outputResourceIds.values().iterator().next());

            ResourceProvider resourceProvider = resourceProviderFactory.getResourceProvider(dataStorageParams.sourcing);
            //noinspection UnnecessaryLocalVariable
            File outputResourceFile = resourceProvider.getOutputResourceFile(
                    taskDir, dispatcher, task, taskParamYaml.taskYaml.outputResourceIds.values().iterator().next(), dataStorageParams);
            return outputResourceFile;
        } catch (ResourceProviderException e) {
            final String msg = "#749.080 Error: " + e.toString();
            log.error(msg, e);
            processorTaskService.markAsFinishedWithError(task.dispatcherUrl, task.taskId, msg);
            return null;
        }
    }
}

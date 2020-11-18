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
import ai.metaheuristic.ai.exceptions.BreakFromLambdaException;
import ai.metaheuristic.ai.exceptions.VariableProviderException;
import ai.metaheuristic.ai.processor.actors.UploadVariableService;
import ai.metaheuristic.ai.processor.env.EnvService;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.processor.tasks.UploadVariableTask;
import ai.metaheuristic.ai.processor.variable_providers.VariableProvider;
import ai.metaheuristic.ai.processor.variable_providers.VariableProviderFactory;
import ai.metaheuristic.ai.utils.asset.AssetFile;
import ai.metaheuristic.ai.utils.asset.AssetUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherSchedule;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorTask;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor
public class ProcessorService {

    private final Globals globals;
    private final ProcessorTaskService processorTaskService;
    private final UploadVariableService uploadResourceActor;
    private final MetadataService metadataService;
    private final DispatcherLookupExtendedService dispatcherLookupExtendedService;
    private final EnvService envService;
    private final VariableProviderFactory resourceProviderFactory;
    private final GitSourcingService gitSourcingService;

    ProcessorCommParamsYaml.ReportProcessorStatus produceReportProcessorStatus(String dispatcherUrl, DispatcherSchedule schedule) {

        // TODO 2019-06-22 why sessionCreatedOn is System.currentTimeMillis()?
        // TODO 2019-08-29 why not? do we have to use a different type?
        // TODO 2020-11-14 or it's about using TimeZoned value?
        ProcessorCommParamsYaml.ReportProcessorStatus status = new ProcessorCommParamsYaml.ReportProcessorStatus(
                envService.getEnvYaml(),
                gitSourcingService.gitStatusInfo,
                schedule.asString,
                metadataService.getSessionId(dispatcherUrl),
                System.currentTimeMillis(),
                "[unknown]", "[unknown]", null, globals.logFile!=null && globals.logFile.exists(),
                TaskParamsYamlUtils.BASE_YAML_UTILS.getDefault().getVersion(),
                globals.os, globals.processorDir.getAbsolutePath());

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
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            processorTaskService.createTask(dispatcherUrl, task.taskId, task.execContextId, task.params);
        }
    }

    public Enums.ResendTaskOutputResourceStatus resendTaskOutputResources(String dispatcherUrl, Long taskId, Long variableId) {
        ProcessorTask task = processorTaskService.findById(dispatcherUrl, taskId);
        if (task==null) {
            return Enums.ResendTaskOutputResourceStatus.TASK_NOT_FOUND;
        }
        final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
        File taskDir = processorTaskService.prepareTaskDir(metadataService.dispatcherUrlAsCode(dispatcherUrl), taskId);

        for (TaskParamsYaml.OutputVariable outputVariable : taskParamYaml.task.outputs) {
            if (!outputVariable.id.equals(variableId)) {
                continue;
            }
            Enums.ResendTaskOutputResourceStatus status;
            switch (outputVariable.sourcing) {
                case dispatcher:
                    status = scheduleSendingToDispatcher(task.dispatcherUrl, taskId, taskDir, outputVariable);
                    break;
                case disk:
                case git:
                case inline:
                default:
                    if (true) {
                        throw new NotImplementedException("need to set 'uploaded' in params for this variableId");
                    }
                    status = Enums.ResendTaskOutputResourceStatus.SEND_SCHEDULED;
                    break;
            }
            if (status!=Enums.ResendTaskOutputResourceStatus.SEND_SCHEDULED) {
                return status;
            }
        }
        return Enums.ResendTaskOutputResourceStatus.SEND_SCHEDULED;
    }

    private Enums.ResendTaskOutputResourceStatus scheduleSendingToDispatcher(String dispatcherUrl, Long taskId, File taskDir, TaskParamsYaml.OutputVariable outputVariable) {
        final AssetFile assetFile = AssetUtils.prepareOutputAssetFile(taskDir, outputVariable.id.toString());

        // is this variable prepared?
        if (assetFile.isError || !assetFile.isContent) {
            log.warn("#749.040 Variable wasn't found. Considering that this task is broken, {}", assetFile);
            processorTaskService.markAsFinishedWithError(dispatcherUrl, taskId,
                    "#749.050 Variable wasn't found. Considering that this task is broken");

            processorTaskService.setCompleted(dispatcherUrl, taskId);
            return Enums.ResendTaskOutputResourceStatus.VARIABLE_NOT_FOUND;
        }
        final Metadata.DispatcherInfo dispatcherCode = metadataService.dispatcherUrlAsCode(dispatcherUrl);
        final DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher =
                dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);

        UploadVariableTask uploadResourceTask = new UploadVariableTask(taskId, assetFile.file, outputVariable.id);
        uploadResourceTask.dispatcher = dispatcher.dispatcherLookup;
        uploadResourceTask.processorId = dispatcherCode.processorId;
        uploadResourceActor.add(uploadResourceTask);

        return Enums.ResendTaskOutputResourceStatus.SEND_SCHEDULED;
    }


    @Data
    public static class ResultOfChecking {
        public boolean isAllLoaded = true;
        public boolean isError = false;
    }

    public ProcessorService.ResultOfChecking checkForPreparingOVariables(ProcessorTask task, Metadata.DispatcherInfo dispatcherCode, TaskParamsYaml taskParamYaml, DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher, File taskDir) {
        ProcessorService.ResultOfChecking result = new ProcessorService.ResultOfChecking();
        try {
            taskParamYaml.task.inputs.forEach(input -> {
                VariableProvider resourceProvider = resourceProviderFactory.getVariableProvider(input.sourcing);
                // the method prepareForDownloadingVariable() is creating a list dynamically. So don't cache the result

                if (task.empty.isEmpty(input.id.toString())) {
                    // variable was initialized and is empty so we don't need to download it again
                    return;
                }
                List<AssetFile> assetFiles = resourceProvider.prepareForDownloadingVariable(taskDir, dispatcher, task, dispatcherCode, input);
                for (AssetFile assetFile : assetFiles) {
                    // is this resource prepared?
                    if (assetFile.isError || !assetFile.isContent) {
                        result.isAllLoaded = false;
                        break;
                    }
                }
            });
        }
        catch (BreakFromLambdaException e) {
            processorTaskService.markAsFinishedWithError(task.dispatcherUrl, task.taskId, e.getMessage());
            result.isError = true;
            return result;
        }
        catch (VariableProviderException e) {
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

    public boolean checkOutputResourceFile(ProcessorTask task, TaskParamsYaml taskParamYaml, DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher, File taskDir) {
        for (TaskParamsYaml.OutputVariable outputVariable : taskParamYaml.task.outputs) {
            try {
                VariableProvider resourceProvider = resourceProviderFactory.getVariableProvider(outputVariable.sourcing);

                //noinspection unused
                File outputResourceFile = resourceProvider.getOutputVariableFromFile(taskDir, dispatcher, task, outputVariable);
            } catch (VariableProviderException e) {
                final String msg = "#749.080 Error: " + e.toString();
                log.error(msg, e);
                processorTaskService.markAsFinishedWithError(task.dispatcherUrl, task.taskId, msg);
                return false;
            }
        }
        return true;
    }
}

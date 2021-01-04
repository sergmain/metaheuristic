/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.ai.commons.dispatcher_schedule.DispatcherSchedule;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorTask;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.env.EnvParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.*;

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
    private final CurrentExecState currentExecState;

//    @Value("${logging.file.name:#{null}}")
    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).toFile( environment.getProperty('logging.file.name' )) }")
    public File logFile;

    KeepAliveRequestParamYaml.ReportProcessor produceReportProcessorStatus(String processorCode, DispatcherUrl dispatcherUrl, DispatcherSchedule schedule) {

        // TODO 2019-06-22 why sessionCreatedOn is System.currentTimeMillis()?
        // TODO 2019-08-29 why not? do we have to use a different type?
        // TODO 2020-11-14 or it's about using TimeZoned value?
        KeepAliveRequestParamYaml.ReportProcessor status = new KeepAliveRequestParamYaml.ReportProcessor(
                to(envService.getEnvYaml()),
                gitSourcingService.gitStatusInfo,
                schedule.asString,
                metadataService.getSessionId(processorCode, dispatcherUrl),
                System.currentTimeMillis(),
                "[unknown]", "[unknown]", null,
                logFile!=null && logFile.exists(),
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

    private KeepAliveRequestParamYaml.Env to(EnvParamsYaml envYaml) {
        KeepAliveRequestParamYaml.Env t = new KeepAliveRequestParamYaml.Env(envYaml.tags);
        t.mirrors.putAll(envYaml.mirrors);
        t.envs.putAll(envYaml.envs);
        envYaml.disk.stream().map(o->new KeepAliveRequestParamYaml.DiskStorage(o.code, o.path)).collect(Collectors.toCollection(() -> t.disk));
        return t;
    }

    /**
     * mark tasks as delivered.
     * By delivering it means that result of exec was delivered to dispatcher
     *
     * @param dispatcherUrl String
     * @param ids List&lt;String> list if task ids
     */
    public void markAsDelivered(String processorCode, DispatcherUrl dispatcherUrl, List<Long> ids) {
        for (Long id : ids) {
            processorTaskService.setDelivered(processorCode, dispatcherUrl, id);
        }
    }

    public void assignTasks(String processorCode, DispatcherUrl dispatcherUrl, DispatcherCommParamsYaml.AssignedTask task) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            currentExecState.registerDelta(dispatcherUrl, List.of(new KeepAliveResponseParamYaml.ExecContextStatus.SimpleStatus(task.execContextId, task.state)));
            processorTaskService.createTask(processorCode, dispatcherUrl, task.taskId, task.execContextId, task.params);
        }
    }

    public Enums.ResendTaskOutputResourceStatus resendTaskOutputResources(String processorCode, DispatcherUrl dispatcherUrl, Long taskId, Long variableId) {
        ProcessorTask task = processorTaskService.findById(processorCode, dispatcherUrl, taskId);
        if (task==null) {
            return Enums.ResendTaskOutputResourceStatus.TASK_NOT_FOUND;
        }
        final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
        File taskDir = processorTaskService.prepareTaskDir(processorCode, metadataService.processorStateBydispatcherUrl(processorCode, dispatcherUrl), taskId);

        for (TaskParamsYaml.OutputVariable outputVariable : taskParamYaml.task.outputs) {
            if (!outputVariable.id.equals(variableId)) {
                continue;
            }
            Enums.ResendTaskOutputResourceStatus status;
            switch (outputVariable.sourcing) {
                case dispatcher:
                    status = scheduleSendingToDispatcher(processorCode, dispatcherUrl, taskId, taskDir, outputVariable);
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

    private Enums.ResendTaskOutputResourceStatus scheduleSendingToDispatcher(String processorCode, DispatcherUrl dispatcherUrl, Long taskId, File taskDir, TaskParamsYaml.OutputVariable outputVariable) {
        final AssetFile assetFile = AssetUtils.prepareOutputAssetFile(taskDir, outputVariable.id.toString());

        // is this variable prepared?
        if (assetFile.isError || !assetFile.isContent) {
            log.warn("#749.040 Variable wasn't found. Considering that this task is broken, {}", assetFile);
            processorTaskService.markAsFinishedWithError(processorCode, dispatcherUrl, taskId,
                    "#749.050 Variable wasn't found. Considering that this task is broken");

            processorTaskService.setCompleted(processorCode, dispatcherUrl, taskId);
            return Enums.ResendTaskOutputResourceStatus.VARIABLE_NOT_FOUND;
        }
        final MetadataParamsYaml.ProcessorState processorState = metadataService.processorStateBydispatcherUrl(processorCode, dispatcherUrl);
        if (S.b(processorState.processorId) ) {
            // at this point processorId must be checked against null
            throw new IllegalStateException("(S.b(processorState.processorId)");
        }

        final DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher =
                dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);

        UploadVariableTask uploadResourceTask = new UploadVariableTask(taskId, assetFile.file, outputVariable.id, processorState.processorId, dispatcher.dispatcherLookup, processorCode);
        uploadResourceActor.add(uploadResourceTask);

        return Enums.ResendTaskOutputResourceStatus.SEND_SCHEDULED;
    }


    @Data
    public static class ResultOfChecking {
        public boolean isAllLoaded = true;
        public boolean isError = false;
    }

    public ProcessorService.ResultOfChecking checkForPreparingVariables(String processorCode, ProcessorTask task, MetadataParamsYaml.ProcessorState processorState, TaskParamsYaml taskParamYaml, DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher, File taskDir) {
        ProcessorService.ResultOfChecking result = new ProcessorService.ResultOfChecking();
        DispatcherUrl dispatcherUrl = new DispatcherUrl(task.dispatcherUrl);
        try {
            taskParamYaml.task.inputs.forEach(input -> {
                VariableProvider resourceProvider = resourceProviderFactory.getVariableProvider(input.sourcing);
                // the method prepareForDownloadingVariable() is creating a list dynamically. So don't cache the result

                if (task.empty.isEmpty(input.id.toString())) {
                    // variable was initialized and is empty so we don't need to download it again
                    return;
                }
                List<AssetFile> assetFiles = resourceProvider.prepareForDownloadingVariable(processorCode, taskDir, dispatcher, task, processorState, input);
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
            processorTaskService.markAsFinishedWithError(processorCode, dispatcherUrl, task.taskId, e.getMessage());
            result.isError = true;
            return result;
        }
        catch (VariableProviderException e) {
            log.error("#749.070 Error", e);
            processorTaskService.markAsFinishedWithError(processorCode, dispatcherUrl, task.taskId, e.toString());
            result.isError = true;
            return result;
        }
        if (result.isError) {
            return result;
        }
        if (!result.isAllLoaded) {
            if (task.assetsPrepared) {
                processorTaskService.markAsAssetPrepared(processorCode, dispatcherUrl, task.taskId, false);
            }
            result.isError = true;
            return result;
        }
        result.isError = false;
        return result;
    }

    public boolean checkOutputResourceFile(String processorCode, ProcessorTask task, TaskParamsYaml taskParamYaml, DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher, File taskDir) {
        DispatcherUrl dispatcherUrl = new DispatcherUrl(task.dispatcherUrl);
        for (TaskParamsYaml.OutputVariable outputVariable : taskParamYaml.task.outputs) {
            try {
                VariableProvider resourceProvider = resourceProviderFactory.getVariableProvider(outputVariable.sourcing);

                //noinspection unused
                File outputResourceFile = resourceProvider.getOutputVariableFromFile(processorCode, taskDir, dispatcher, task, outputVariable);
            } catch (VariableProviderException e) {
                final String msg = "#749.080 Error: " + e.toString();
                log.error(msg, e);
                processorTaskService.markAsFinishedWithError(processorCode, dispatcherUrl, task.taskId, msg);
                return false;
            }
        }
        return true;
    }
}

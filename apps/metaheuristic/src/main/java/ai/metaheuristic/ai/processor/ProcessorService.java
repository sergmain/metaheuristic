/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import ai.metaheuristic.ai.commons.dispatcher_schedule.DispatcherSchedule;
import ai.metaheuristic.ai.exceptions.BreakFromLambdaException;
import ai.metaheuristic.ai.exceptions.VariableProviderException;
import ai.metaheuristic.ai.processor.actors.UploadVariableService;
import ai.metaheuristic.ai.processor.data.ProcessorData;
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
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorTask;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.env.EnvParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    KeepAliveRequestParamYaml.ReportProcessor produceReportProcessorStatus(ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref, DispatcherSchedule schedule) {

        // TODO 2019-06-22 why sessionCreatedOn is System.currentTimeMillis()?
        // TODO 2019-08-29 why not? do we have to use a different type?
        // TODO 2020-11-14 or it's about using TimeZoned value?
        final File processorFile = new File(globals.processor.dir.dir, ref.processorCode);
        KeepAliveRequestParamYaml.ReportProcessor status = new KeepAliveRequestParamYaml.ReportProcessor(
                to(envService.getEnvParamsYaml(), envService.getTags(ref.processorCode)),
                gitSourcingService.gitStatusInfo,
                schedule.asString,
                metadataService.getSessionId(ref.processorCode, ref.dispatcherUrl),
                System.currentTimeMillis(),
                "[unknown]", "[unknown]", null,
                logFile!=null && logFile.exists(),
                TaskParamsYamlUtils.BASE_YAML_UTILS.getDefault().getVersion(),
                globals.os, processorFile.getAbsolutePath());

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

    private static KeepAliveRequestParamYaml.Env to(EnvParamsYaml envYaml, @Nullable String tags) {
        KeepAliveRequestParamYaml.Env t = new KeepAliveRequestParamYaml.Env(tags);
        t.mirrors.putAll(envYaml.mirrors);
        t.envs.putAll(envYaml.envs);
        t.quotas.limit = envYaml.quotas.limit;
        t.quotas.disabled = envYaml.quotas.disabled;
        t.quotas.defaultValue = envYaml.quotas.defaultValue;
        envYaml.quotas.values.stream().map(o->new KeepAliveRequestParamYaml.Quota(o.tag, o.amount)).collect(Collectors.toCollection(()->t.quotas.values));
        envYaml.disk.stream().map(o->new KeepAliveRequestParamYaml.DiskStorage(o.code, o.path)).collect(Collectors.toCollection(() -> t.disk));
        return t;
    }

    /**
     * mark tasks as delivered.
     * By delivering it means that result of exec was delivered to dispatcher
     *
     * @param ref ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef
     * @param ids List&lt;String> list if task ids
     */
    public void markAsDelivered(ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref, List<Long> ids) {
        for (Long id : ids) {
            processorTaskService.setDelivered(ref, id);
        }
    }

    public void assignTasks(ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref, DispatcherCommParamsYaml.AssignedTask task) {
        synchronized (ProcessorSyncHolder.processorGlobalSync) {
            currentExecState.registerDelta(ref.dispatcherUrl, List.of(new KeepAliveResponseParamYaml.ExecContextStatus.SimpleStatus(task.execContextId, task.state)));
            processorTaskService.createTask(ref, task);
        }
    }

    public List<ProcessorCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus> getResendTaskOutputResourceResultStatus(
            ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref, DispatcherCommParamsYaml.DispatcherResponse response) {
        if (response.resendTaskOutputs==null || response.resendTaskOutputs.resends.isEmpty()) {
            return List.of();
        }
        List<ProcessorCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus> statuses = new ArrayList<>();
        for (DispatcherCommParamsYaml.ResendTaskOutput output : response.resendTaskOutputs.resends) {
            Enums.ResendTaskOutputResourceStatus status = resendTaskOutputResources(ref, output.taskId, output.variableId);
            statuses.add( new ProcessorCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus(output.taskId, output.variableId, status));
        }
        return statuses;
    }

    public Enums.ResendTaskOutputResourceStatus resendTaskOutputResources(ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref, Long taskId, Long variableId) {
        ProcessorTask task = processorTaskService.findById(ref, taskId);
        if (task==null) {
            return Enums.ResendTaskOutputResourceStatus.TASK_NOT_FOUND;
        }
        final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
        File taskDir = processorTaskService.prepareTaskDir(ref, taskId);

        for (TaskParamsYaml.OutputVariable outputVariable : taskParamYaml.task.outputs) {
            if (!outputVariable.id.equals(variableId)) {
                continue;
            }
            Enums.ResendTaskOutputResourceStatus status;
            switch (outputVariable.sourcing) {
                case dispatcher:
                    status = scheduleSendingToDispatcher(ref, taskId, taskDir, outputVariable);
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

    private Enums.ResendTaskOutputResourceStatus scheduleSendingToDispatcher(ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref, Long taskId, File taskDir, TaskParamsYaml.OutputVariable outputVariable) {
        final AssetFile assetFile = AssetUtils.prepareOutputAssetFile(taskDir, outputVariable.id.toString());

        // is this variable prepared?
        if (assetFile.isError || !assetFile.isContent) {
            log.warn("#749.040 Variable wasn't found. Considering that this task is broken, {}", assetFile);
            processorTaskService.markAsFinishedWithError(ref, taskId,
                    "#749.050 Variable #"+outputVariable.id+" wasn't found. Considering that this task is broken");

            processorTaskService.setCompleted(ref, taskId);
            return Enums.ResendTaskOutputResourceStatus.VARIABLE_NOT_FOUND;
        }

        final DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher =
                dispatcherLookupExtendedService.lookupExtendedMap.get(ref.dispatcherUrl);

        UploadVariableTask uploadResourceTask = new UploadVariableTask(taskId, assetFile.file, outputVariable.id, ref, dispatcher.dispatcherLookup);
        uploadResourceActor.add(uploadResourceTask);

        return Enums.ResendTaskOutputResourceStatus.SEND_SCHEDULED;
    }


    @Data
    public static class ResultOfChecking {
        public boolean isAllLoaded = true;
        public boolean isError = false;
    }

    public ProcessorService.ResultOfChecking checkForPreparingVariables(
            ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref, ProcessorTask task, MetadataParamsYaml.ProcessorState processorState,
            TaskParamsYaml taskParamYaml, DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher, File taskDir) {
        ProcessorService.ResultOfChecking result = new ProcessorService.ResultOfChecking();
        if (!ref.dispatcherUrl.url.equals(task.dispatcherUrl)) {
            throw new IllegalStateException("(!ref.dispatcherUrl.url.equals(task.dispatcherUrl))");
        }
        try {
            taskParamYaml.task.inputs.forEach(input -> {
                VariableProvider resourceProvider = resourceProviderFactory.getVariableProvider(input.sourcing);
                if (task.empty.isEmpty(input.id.toString())) {
                    // variable was initialized and is empty so we don't need to download it again
                    return;
                }

                // the method prepareForDownloadingVariable() is creating a list dynamically. So don't cache the result
                List<AssetFile> assetFiles = resourceProvider.prepareForDownloadingVariable(ref, taskDir, dispatcher, task, processorState, input);
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
            processorTaskService.markAsFinishedWithError(ref, task.taskId, e.getMessage());
            result.isError = true;
            return result;
        }
        catch (VariableProviderException e) {
            log.error("#749.070 Error", e);
            processorTaskService.markAsFinishedWithError(ref, task.taskId, e.toString());
            result.isError = true;
            return result;
        }
        if (result.isError) {
            return result;
        }
        if (!result.isAllLoaded) {
            if (task.assetsPrepared) {
                processorTaskService.markAsAssetPrepared(ref, task.taskId, false);
            }
            result.isError = true;
            return result;
        }
        //noinspection ConstantConditions
        result.isError = false;
        return result;
    }

    public boolean checkOutputResourceFile(ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref, ProcessorTask task, TaskParamsYaml taskParamYaml, DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher, File taskDir) {
        for (TaskParamsYaml.OutputVariable outputVariable : taskParamYaml.task.outputs) {
            try {
                VariableProvider resourceProvider = resourceProviderFactory.getVariableProvider(outputVariable.sourcing);

                //noinspection unused
                File outputResourceFile = resourceProvider.getOutputVariableFromFile(ref, taskDir, dispatcher, task, outputVariable);
            } catch (VariableProviderException e) {
                final String msg = "#749.080 Error: " + e.toString();
                log.error(msg, e);
                processorTaskService.markAsFinishedWithError(ref, task.taskId, msg);
                return false;
            }
        }
        return true;
    }
}

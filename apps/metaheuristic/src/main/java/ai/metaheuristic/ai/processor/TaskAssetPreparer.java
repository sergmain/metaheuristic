/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.commons.CommonSync;
import ai.metaheuristic.ai.functions.DownloadFunctionService;
import ai.metaheuristic.ai.functions.DownloadGitFunctionService;
import ai.metaheuristic.ai.functions.FunctionRepositoryData;
import ai.metaheuristic.ai.functions.FunctionRepositoryProcessorService;
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.processor.event.AssetPreparingForProcessorTaskEvent;
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupExtendedParams;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorCoreTask;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.functions.FunctionEnums.DownloadPriority.NORMAL;
import static ai.metaheuristic.ai.functions.communication.FunctionRepositoryResponseParams.*;

@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class TaskAssetPreparer {

    private final Globals globals;
    private final DownloadFunctionService downloadFunctionService;
    private final DownloadGitFunctionService downloadGitFunctionService;
    private final CurrentExecState currentExecState;
    private final ProcessorTaskService processorTaskService;
    private final ProcessorEnvironment processorEnvironment;
    private final ProcessorService processorService;
    private final ApplicationEventPublisher eventPublisher;
    private final FunctionRepositoryProcessorService functionRepositoryProcessorService;

    public static class TaskAssetPreparingSync {
        private static final CommonSync<String> commonSync = new CommonSync<>();

        public static <T> T getWithSync(final String processorCode, Supplier<T> supplier) {
            TxUtils.checkTxNotExists();
            final ReentrantReadWriteLock.WriteLock lock = commonSync.getWriteLock(processorCode);
            try {
                lock.lock();
                return supplier.get();
            } finally {
                lock.unlock();
            }
        }

        @Nullable
        public static <T> T getWithSyncNullable(final String processorCode, Supplier<T> supplier) {
            TxUtils.checkTxNotExists();
            final ReentrantReadWriteLock.WriteLock lock = commonSync.getWriteLock(processorCode);
            try {
                lock.lock();
                return supplier.get();
            } finally {
                lock.unlock();
            }
        }
    }

    public void fixedDelay() {
        if (globals.testing) {
            return;
        }
        if (!globals.processor.enabled) {
            return;
        }

        for (ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core : processorEnvironment.metadataParams.getAllEnabledRefsForCores()) {

            processorTaskService.findAllForCore(core).forEach(task -> {
                ProcessorAndCoreData.DispatcherUrl dispatcherUrl = new ProcessorAndCoreData.DispatcherUrl(task.dispatcherUrl);
                // delete all orphan tasks
                if (EnumsApi.ExecContextState.DOESNT_EXIST == currentExecState.getState(dispatcherUrl, task.execContextId)) {
                    processorTaskService.delete(core, task.taskId);
                    log.info("951.030 orphan task was deleted, taskId: #{}, url: {}, execContextId: {}", task.taskId, task.dispatcherUrl, task.execContextId);
                    log.info("951.060  registered execContexts: {}", currentExecState.getExecContextsNormalized(dispatcherUrl));
                }
                // mark as Reported and Delivered for all finished ExecContexts
                if ((!task.isReported() || !task.isDelivered()) && currentExecState.finished(core.dispatcherUrl, task.execContextId)) {
                    log.info("951.090 Found not-reported and/or not-delivered task, taskId: #{}, url: {}, execContextId: {}", task.taskId, task.dispatcherUrl, task.execContextId);
                    processorTaskService.setReportedOn(core, task.taskId);
                    processorTaskService.setDelivered(core, task.taskId);
                }
            });

            // find all tasks which weren't finished and resources aren't prepared yet
            List<ProcessorCoreTask> tasks = processorTaskService.findAllByCompetedIsFalseAndFinishedOnIsNullAndAssetsPreparedIs(core, false);
            if (tasks.size()>1) {
                log.warn("951.150 There is more than one task: {}", tasks.stream().map(ProcessorCoreTask::getTaskId).collect(Collectors.toList()));
            }

            for (ProcessorCoreTask task : tasks) {
                eventPublisher.publishEvent(new AssetPreparingForProcessorTaskEvent(core, task.taskId));
            }
        }
    }

    @Async
    @EventListener
    public void processAssetPreparing(AssetPreparingForProcessorTaskEvent event) {
        try {
            TaskAssetPreparingSync.getWithSyncNullable(event.core.coreCode,
                    ()-> processAssetPreparingInternal(event.core, event.taskId));
        } catch (Throwable th) {
            log.error("951.180 Error, need to investigate ", th);
        }
    }

    private Void processAssetPreparingInternal(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId) {
        ProcessorCoreTask task = processorTaskService.findByIdForCore(core, taskId);
        if (task==null) {
            return null;
        }
        if (S.b(task.dispatcherUrl)) {
            log.error("951.210 dispatcherUrl for task {} is blank", task.getTaskId());
            return null;
        }
        if (S.b(task.getParams())) {
            log.error("951.240 Params for task {} is blank", task.getTaskId());
            return null;
        }

        final DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher =
                processorEnvironment.dispatcherLookupExtendedService.lookupExtendedMap.get(core.dispatcherUrl);

        final MetadataParamsYaml.ProcessorSession processorState = processorEnvironment.metadataParams.processorStateByDispatcherUrl(core);
        if (processorState.processorId==null || S.b(processorState.sessionId)) {
            log.warn("951.270 processor {} with dispatcher {} isn't ready", core.coreCode, core.dispatcherUrl.url);
            return null;
        }

        final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl = new ProcessorAndCoreData.AssetManagerUrl(dispatcher.dispatcherLookup.assetManagerUrl);

        if (EnumsApi.ExecContextState.DOESNT_EXIST == currentExecState.getState(core.dispatcherUrl, task.execContextId)) {
            processorTaskService.delete(core, task.taskId);
            log.info("951.300 Deleted orphan task {}", task);
            return null;
        }
        final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.UTILS.to(task.getParams());

        // Start preparing data for function
        Path taskDir = processorTaskService.prepareTaskDir(core, task.taskId);
        ProcessorData.ResultOfChecking resultOfChecking = processorService.checkForPreparingVariables(core, task, processorState, taskParamYaml, dispatcher, taskDir);
        if (resultOfChecking.isError) {
            return null;
        }

        // start preparing functions
        final AtomicBoolean isAllReady = new AtomicBoolean(resultOfChecking.isAllLoaded);
        final TaskParamsYaml.FunctionConfig functionConfig = taskParamYaml.task.function;
        if ( !checkFunctionPreparedness(core, functionConfig, assetManagerUrl, task.taskId) ) {
            isAllReady.set(false);
        }
        taskParamYaml.task.preFunctions.forEach(sc-> {
            if ( !checkFunctionPreparedness(core, sc, assetManagerUrl, task.taskId) ) {
                isAllReady.set(false);
            }
        });
        taskParamYaml.task.postFunctions.forEach(sc-> {
            if ( !checkFunctionPreparedness(core, sc, assetManagerUrl, task.taskId) ) {
                isAllReady.set(false);
            }
        });

        // update the status of task if everything is prepared
        if (isAllReady.get()) {
            log.info("951.330 All assets were prepared for task #{}, dispatcher: {}", task.taskId, task.dispatcherUrl);
            processorTaskService.markAsAssetPrepared(core, task.taskId, true);
        }
        return null;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean checkFunctionPreparedness(
            ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core,
            TaskParamsYaml.FunctionConfig functionConfig, ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, Long taskId) {

        ShortFunctionConfig shortFunctionConfig =
            new ShortFunctionConfig(functionConfig.code, functionConfig.sourcing, functionConfig.git);

        if (functionConfig.sourcing== EnumsApi.FunctionSourcing.dispatcher) {
            return checkFunctionPreparednessWithDispatcher(core, functionConfig, assetManagerUrl, taskId, shortFunctionConfig);
        }
        else if (functionConfig.sourcing== EnumsApi.FunctionSourcing.git) {
            return checkFunctionPreparednessWithGit(core, functionConfig, assetManagerUrl, taskId, shortFunctionConfig);
        }
        return true;
    }

    private boolean checkFunctionPreparednessWithGit(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, TaskParamsYaml.FunctionConfig functionConfig, ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, Long taskId, ShortFunctionConfig shortFunctionConfig) {
        final FunctionRepositoryData.DownloadStatus functionDownloadStatuses = FunctionRepositoryProcessorService.getFunctionDownloadStatus(assetManagerUrl, functionConfig.code);
        if (functionDownloadStatuses==null) {
            return false;
        }
        final DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher =
                processorEnvironment.dispatcherLookupExtendedService.lookupExtendedMap.get(core.dispatcherUrl);

        final EnumsApi.FunctionState functionState = functionDownloadStatuses.state;
        if (functionState == EnumsApi.FunctionState.none) {
            downloadGitFunctionService.addTask(new FunctionRepositoryData.DownloadFunctionTask(functionConfig.code, shortFunctionConfig, assetManagerUrl, dispatcher.dispatcherLookup.signatureRequired, NORMAL));
            return false;
        }
        else {
            if (functionState== EnumsApi.FunctionState.function_config_error || functionState== EnumsApi.FunctionState.download_error) {
                log.error("951.360 The function {} has a state as {}, start re-downloading", functionConfig.code, functionState);

                FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionConfig.code, EnumsApi.FunctionState.none, null);

                downloadGitFunctionService.addTask(new FunctionRepositoryData.DownloadFunctionTask(functionConfig.code, shortFunctionConfig, assetManagerUrl, dispatcher.dispatcherLookup.signatureRequired, NORMAL));
                return true;
            }
            else if (functionState== EnumsApi.FunctionState.dispatcher_config_error) {
                processorTaskService.markAsFinishedWithError(core,
                    taskId,
                        S.f("951.390 Task #%d can't be processed because dispatcher at %s was mis-configured and function %s can't downloaded",
                            taskId, core.dispatcherUrl.url, functionConfig.code));
            }
            if (functionState!= EnumsApi.FunctionState.ready) {
                log.warn("951.420 Function {} has broken state as {}", functionConfig.code, functionState);
            }
            return functionState == EnumsApi.FunctionState.ready;
        }
    }

    private boolean checkFunctionPreparednessWithDispatcher(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, TaskParamsYaml.FunctionConfig functionConfig, ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, Long taskId, ShortFunctionConfig shortFunctionConfig) {
        final FunctionRepositoryData.DownloadStatus functionDownloadStatus = FunctionRepositoryProcessorService.getFunctionDownloadStatus(assetManagerUrl, functionConfig.code);
        if (functionDownloadStatus==null) {
            return false;
        }
        final DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher =
                processorEnvironment.dispatcherLookupExtendedService.lookupExtendedMap.get(core.dispatcherUrl);

        final EnumsApi.FunctionState functionState = functionDownloadStatus.state;
        if (functionState == EnumsApi.FunctionState.none) {
            downloadFunctionService.addTask(new FunctionRepositoryData.DownloadFunctionTask(functionConfig.code, shortFunctionConfig, assetManagerUrl, dispatcher.dispatcherLookup.signatureRequired, NORMAL));
            return false;
        }
        else {
            if (functionState== EnumsApi.FunctionState.function_config_error || functionState== EnumsApi.FunctionState.download_error) {
                log.error("951.360 The function {} has a state as {}, start re-downloading", functionConfig.code, functionState);

                FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionConfig.code, EnumsApi.FunctionState.none, null);

                downloadFunctionService.addTask(new FunctionRepositoryData.DownloadFunctionTask(functionConfig.code, shortFunctionConfig, assetManagerUrl, dispatcher.dispatcherLookup.signatureRequired, NORMAL));
                return true;
            }
            else if (functionState== EnumsApi.FunctionState.dispatcher_config_error) {
                processorTaskService.markAsFinishedWithError(core,
                    taskId,
                        S.f("951.390 Task #%d can't be processed because dispatcher at %s was mis-configured and function %s can't downloaded",
                            taskId, core.dispatcherUrl.url, functionConfig.code));
            }
            if (functionState!= EnumsApi.FunctionState.ready) {
                log.warn("951.420 Function {} has broken state as {}", functionConfig.code, functionState);
            }
            return functionState == EnumsApi.FunctionState.ready;
        }
    }

}

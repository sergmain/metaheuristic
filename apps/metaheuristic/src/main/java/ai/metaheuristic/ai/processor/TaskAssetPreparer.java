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
import ai.metaheuristic.ai.processor.actors.DownloadFunctionService;
import ai.metaheuristic.ai.processor.tasks.DownloadFunctionTask;
import ai.metaheuristic.ai.yaml.metadata.FunctionDownloadStatusYaml;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorTask;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor
public class TaskAssetPreparer {

    private final Globals globals;
    private final DownloadFunctionService downloadFunctionActor;
    private final CurrentExecState currentExecState;
    private final ProcessorTaskService processorTaskService;
    private final DispatcherLookupExtendedService dispatcherLookupExtendedService;
    private final MetadataService metadataService;
    private final ProcessorService processorService;

    public void fixedDelay() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.processorEnabled) {
            return;
        }

        // delete all orphan tasks
        processorTaskService.findAll().forEach(task -> {
            if (EnumsApi.ExecContextState.DOESNT_EXIST == currentExecState.getState(task.dispatcherUrl, task.execContextId)) {
//                processorTaskService.delete(task.dispatcherUrl, task.taskId);

                log.info("#951.010 Deleted orphan task #{}, url: {}, execContextId: {}", task.taskId, task.dispatcherUrl, task.execContextId);
                log.info("#951.010  registered execContext: {}", currentExecState.getExecContexts(task.dispatcherUrl));
            }
        });

        // find all tasks which weren't finished and resources aren't prepared yet
        List<ProcessorTask> tasks = processorTaskService.findAllByCompetedIsFalseAndFinishedOnIsNullAndAssetsPreparedIs(false);
        if (tasks.size()>1) {
            log.warn("#951.020 There is more than one task: {}", tasks.stream().map(ProcessorTask::getTaskId).collect(Collectors.toList()));
        }
        for (ProcessorTask task : tasks) {
            if (StringUtils.isBlank(task.dispatcherUrl)) {
                log.error("#951.040 dispatcherUrl for task {} is blank", task.getTaskId());
                continue;
            }
            if (StringUtils.isBlank(task.getParams())) {
                log.error("#951.060 Params for task {} is blank", task.getTaskId());
                continue;
            }
            MetadataParamsYaml.ProcessorState processorState = metadataService.dispatcherUrlAsCode(task.dispatcherUrl);

            if (EnumsApi.ExecContextState.DOESNT_EXIST == currentExecState.getState(task.dispatcherUrl, task.execContextId)) {
                processorTaskService.delete(task.dispatcherUrl, task.taskId);
                log.info("#951.080 Deleted orphan task {}", task);
                continue;
            }
            final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
/*
            if (CollectionUtils.isEmpty(taskParamYaml.task.inputs)) {
                log.info("#951.100 task.inputs is empty\n{}", task.getParams());
                continue;
            }
*/
            final DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher =
                    dispatcherLookupExtendedService.lookupExtendedMap.get(task.dispatcherUrl);

            // process only if dispatcher has already sent its config
            if (dispatcher.context.chunkSize==null) {
                log.warn("#951.120 Dispatcher {} doesn't provide chunkSize", task.dispatcherUrl);
                continue;
            }

            // Start preparing data for function
            File taskDir = processorTaskService.prepareTaskDir(processorState, task.taskId);
            ProcessorService.ResultOfChecking resultOfChecking = processorService.checkForPreparingOVariables(task, processorState, taskParamYaml, dispatcher, taskDir);
            if (resultOfChecking.isError) {
                continue;
            }

            // start preparing functions
            final AtomicBoolean isAllReady = new AtomicBoolean(resultOfChecking.isAllLoaded);
            final TaskParamsYaml.FunctionConfig functionConfig = taskParamYaml.task.function;
            if ( !checkFunctionPreparedness(functionConfig, task.dispatcherUrl, dispatcher, processorState.processorId, task.taskId) ) {
                isAllReady.set(false);
            }
            taskParamYaml.task.preFunctions.forEach(sc-> {
                if ( !checkFunctionPreparedness(sc, task.dispatcherUrl, dispatcher, processorState.processorId, task.taskId) ) {
                    isAllReady.set(false);
                }
            });
            taskParamYaml.task.postFunctions.forEach(sc-> {
                if ( !checkFunctionPreparedness(sc, task.dispatcherUrl, dispatcher, processorState.processorId, task.taskId) ) {
                    isAllReady.set(false);
                }
            });

            // update the status of task if everything is prepared
            if (isAllReady.get()) {
                log.info("#951.140 All assets were prepared for task #{}, dispatcher: {}", task.taskId, task.dispatcherUrl);
                processorTaskService.markAsAssetPrepared(task.dispatcherUrl, task.taskId, true);
            }
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean checkFunctionPreparedness(TaskParamsYaml.FunctionConfig functionConfig, String dispatcherUrl,
                                              DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher, String processorId, Long taskId) {
        if (functionConfig.sourcing== EnumsApi.FunctionSourcing.dispatcher) {
            final FunctionDownloadStatusYaml.Status functionDownloadStatuses = metadataService.getFunctionDownloadStatuses(dispatcherUrl, functionConfig.code);
            if (functionDownloadStatuses==null) {
                return false;
            }
            final Enums.FunctionState functionState = functionDownloadStatuses.functionState;
            if (functionState == Enums.FunctionState.none) {
                DownloadFunctionTask functionTask = new DownloadFunctionTask(dispatcher.context.chunkSize, functionConfig.code, functionConfig);
                functionTask.dispatcher = dispatcher.dispatcherLookup;
                functionTask.processorId = processorId;
                downloadFunctionActor.add(functionTask);
                return false;
            }
            else {
//                if (functionState==Enums.FunctionState.checksum_wrong || functionState==Enums.FunctionState.function_config_error || functionState==Enums.FunctionState.download_error) {
                if (functionState==Enums.FunctionState.function_config_error || functionState==Enums.FunctionState.download_error) {
                    log.error("#951.170 The function {} has a state as {}, start re-downloading", functionConfig.code, functionState);

                    metadataService.setFunctionState(dispatcher.dispatcherLookup.getAsset().url, functionConfig.code, Enums.FunctionState.none);

                    DownloadFunctionTask functionTask = new DownloadFunctionTask(dispatcher.context.chunkSize, functionConfig.code, functionConfig);
                    functionTask.dispatcher = dispatcher.dispatcherLookup;
                    functionTask.processorId = processorId;
                    downloadFunctionActor.add(functionTask);
                    return true;
                }
                else if (functionState==Enums.FunctionState.dispatcher_config_error) {
                    processorTaskService.markAsFinishedWithError(dispatcherUrl,
                            taskId,
                            S.f("Task #%d can't be processed because dispatcher at %s was mis-configured and function %s can't downloaded",
                            taskId, dispatcherUrl, functionConfig.code));
                }
                if (functionState!= Enums.FunctionState.ready) {
                    log.warn("#951.180 Function {} has broken state as {}", functionConfig.code, functionState);
                }
                return functionState == Enums.FunctionState.ready;
            }
        }
        return true;
    }
}

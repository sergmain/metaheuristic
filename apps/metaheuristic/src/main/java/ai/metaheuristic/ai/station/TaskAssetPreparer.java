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
import ai.metaheuristic.ai.station.actors.DownloadFunctionActor;
import ai.metaheuristic.ai.station.tasks.DownloadFunctionTask;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.metadata.FunctionDownloadStatusYaml;
import ai.metaheuristic.ai.yaml.station_task.StationTask;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
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
@Profile("station")
@RequiredArgsConstructor
public class TaskAssetPreparer {

    private final Globals globals;
    private final DownloadFunctionActor downloadFunctionActor;
    private final CurrentExecState currentExecState;
    private final StationTaskService stationTaskService;
    private final DispatcherLookupExtendedService dispatcherLookupExtendedService;
    private final MetadataService metadataService;
    private final StationService stationService;

    public void fixedDelay() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }

        // delete all orphan tasks
        stationTaskService.findAll().forEach(task -> {
            if (EnumsApi.ExecContextState.DOESNT_EXIST == currentExecState.getState(task.launchpadUrl, task.execContextId)) {
                stationTaskService.delete(task.launchpadUrl, task.taskId);
                log.info("Deleted orphan task #{}", task.taskId);
            }
        });

        // find all tasks which weren't finished and resources aren't prepared yet
        List<StationTask> tasks = stationTaskService.findAllByCompetedIsFalseAndFinishedOnIsNullAndAssetsPreparedIs(false);
        if (tasks.size()>1) {
            log.warn("#951.010 There is more than one task: {}", tasks.stream().map(StationTask::getTaskId).collect(Collectors.toList()));
        }
        for (StationTask task : tasks) {
            if (StringUtils.isBlank(task.launchpadUrl)) {
                log.error("#951.020 launchpadUrl for task {} is blank", task.getTaskId());
                continue;
            }
            if (StringUtils.isBlank(task.getParams())) {
                log.error("#951.030 Params for task {} is blank", task.getTaskId());
                continue;
            }
            Metadata.LaunchpadInfo launchpadInfo = metadataService.launchpadUrlAsCode(task.launchpadUrl);

            if (EnumsApi.ExecContextState.DOESNT_EXIST == currentExecState.getState(task.launchpadUrl, task.execContextId)) {
                stationTaskService.delete(task.launchpadUrl, task.taskId);
                log.info("Deleted orphan task {}", task);
                continue;
            }
            final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
            if (taskParamYaml.taskYaml.inputResourceIds.isEmpty()) {
                log.warn("#951.040 taskParamYaml.inputResourceCodes is empty\n{}", task.getParams());
                continue;
            }
            final DispatcherLookupExtendedService.LaunchpadLookupExtended launchpad =
                    dispatcherLookupExtendedService.lookupExtendedMap.get(task.launchpadUrl);

            // process only if launchpad has already sent its config
            if (launchpad.context.chunkSize==null) {
                log.warn("#951.050 Launchpad {} doesn't provide chunkSize", task.launchpadUrl);
                continue;
            }

            // Start preparing data for function
            File taskDir = stationTaskService.prepareTaskDir(launchpadInfo, task.taskId);
            StationService.ResultOfChecking resultOfChecking = stationService.checkForPreparingOfAssets(task, launchpadInfo, taskParamYaml, launchpad, taskDir);
            if (resultOfChecking.isError) {
                continue;
            }

            // start preparing functions
            final AtomicBoolean isAllReady = new AtomicBoolean(resultOfChecking.isAllLoaded);
            final TaskParamsYaml.FunctionConfig functionConfig = taskParamYaml.taskYaml.function;
            if ( !prepareFunction(functionConfig, task.launchpadUrl, launchpad, launchpadInfo.stationId) ) {
                isAllReady.set(false);
            }
            if (taskParamYaml.taskYaml.preFunctions !=null) {
                taskParamYaml.taskYaml.preFunctions.forEach(sc-> {
                    if ( !prepareFunction(sc, task.launchpadUrl, launchpad, launchpadInfo.stationId) ) {
                        isAllReady.set(false);
                    }
                });
            }
            if (taskParamYaml.taskYaml.postFunctions !=null) {
                taskParamYaml.taskYaml.postFunctions.forEach(sc-> {
                    if ( !prepareFunction(sc, task.launchpadUrl, launchpad, launchpadInfo.stationId) ) {
                        isAllReady.set(false);
                    }
                });
            }

            // update the status of task if everything is prepared
            if (isAllReady.get()) {
                log.info("All assets were prepared for task #{}, launchpad: {}", task.taskId, task.launchpadUrl);
                stationTaskService.markAsAssetPrepared(task.launchpadUrl, task.taskId, true);
            }
        }
    }

    private boolean prepareFunction(TaskParamsYaml.FunctionConfig functionConfig, String launchpadUrl, DispatcherLookupExtendedService.LaunchpadLookupExtended launchpad, String stationId) {
        if (functionConfig.sourcing== EnumsApi.FunctionSourcing.launchpad) {
            final String code = functionConfig.code;
            final FunctionDownloadStatusYaml.Status functionDownloadStatuses = metadataService.getFunctionDownloadStatuses(launchpadUrl, code);
            if (functionDownloadStatuses==null) {
                return false;
            }
            final Enums.FunctionState functionState = functionDownloadStatuses.functionState;
            if (functionState == Enums.FunctionState.none) {
                DownloadFunctionTask functionTask = new DownloadFunctionTask(launchpad.context.chunkSize, functionConfig.getCode(), functionConfig);
                functionTask.launchpad = launchpad.launchpadLookup;
                functionTask.stationId = stationId;
                downloadFunctionActor.add(functionTask);
                return true;
            }
            else {
                return functionState == Enums.FunctionState.ready;
            }
        }
        return true;
    }
}

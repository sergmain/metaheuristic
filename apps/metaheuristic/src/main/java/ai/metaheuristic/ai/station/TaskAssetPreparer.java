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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.station.actors.DownloadSnippetActor;
import ai.metaheuristic.ai.station.tasks.DownloadSnippetTask;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.station_task.StationTask;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.SnippetApiData;
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
    private final DownloadSnippetActor downloadSnippetActor;
    private final CurrentExecState currentExecState;
    private final StationTaskService stationTaskService;
    private final LaunchpadLookupExtendedService launchpadLookupExtendedService;
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
            if (EnumsApi.WorkbookExecState.DOESNT_EXIST == currentExecState.getState(task.launchpadUrl, task.workbookId)) {
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

            if (EnumsApi.WorkbookExecState.DOESNT_EXIST == currentExecState.getState(task.launchpadUrl, task.workbookId)) {
                stationTaskService.delete(task.launchpadUrl, task.taskId);
                log.info("Deleted orphan task {}", task);
                continue;
            }
            final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
            if (taskParamYaml.taskYaml.inputResourceCodes.isEmpty()) {
                log.warn("#951.040 taskParamYaml.inputResourceCodes is empty\n{}", task.getParams());
                continue;
            }
            final LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad =
                    launchpadLookupExtendedService.lookupExtendedMap.get(task.launchpadUrl);

            // process only if launchpad has already sent its config
            if (launchpad.config.chunkSize==null) {
                log.warn("#951.050 Launchpad {} doesn't provide chunkSize", task.launchpadUrl);
                continue;
            }

            // Start preparing data for snippet
            File taskDir = stationTaskService.prepareTaskDir(launchpadInfo, task.taskId);
            StationService.ResultOfChecking resultOfChecking = stationService.checkForPreparingOfAssets(task, launchpadInfo, taskParamYaml, launchpad, taskDir);
            if (resultOfChecking.isError) {
                continue;
            }

            // start preparing snippets
            final AtomicBoolean isAllReady = new AtomicBoolean(resultOfChecking.isAllLoaded);
            final SnippetApiData.SnippetConfig snippetConfig = taskParamYaml.taskYaml.snippet;
            if ( !prepareSnippet(snippetConfig, task.launchpadUrl, launchpad, launchpadInfo.stationId) ) {
                isAllReady.set(false);
            }
            if (taskParamYaml.taskYaml.preSnippets!=null) {
                taskParamYaml.taskYaml.preSnippets.forEach( sc-> {
                    if ( !prepareSnippet(sc, task.launchpadUrl, launchpad, launchpadInfo.stationId) ) {
                        isAllReady.set(false);
                    }
                });
            }
            if (taskParamYaml.taskYaml.postSnippets!=null) {
                taskParamYaml.taskYaml.postSnippets.forEach( sc-> {
                    if ( !prepareSnippet(sc, task.launchpadUrl, launchpad, launchpadInfo.stationId) ) {
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

    private boolean prepareSnippet(SnippetApiData.SnippetConfig snippetConfig, String launchpadUrl, LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad, String stationId) {
        if (snippetConfig.sourcing==EnumsApi.SnippetSourcing.launchpad) {
            final String code = snippetConfig.code;
            final Enums.SnippetState snippetState = metadataService.getSnippetDownloadStatuses(launchpadUrl, code).snippetState;
            if (snippetState==Enums.SnippetState.none) {
                DownloadSnippetTask snippetTask = new DownloadSnippetTask(launchpad.config.chunkSize, snippetConfig.getCode(), snippetConfig);
                snippetTask.launchpad = launchpad.launchpadLookup;
                snippetTask.stationId = stationId;
                downloadSnippetActor.add(snippetTask);
                return true;
            }
            else {
                return snippetState == Enums.SnippetState.ready;
            }
        }
        return true;
    }
}

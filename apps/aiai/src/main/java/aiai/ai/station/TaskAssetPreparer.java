/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package aiai.ai.station;

import aiai.ai.Globals;
import aiai.ai.resource.AssetFile;
import aiai.ai.resource.ResourceUtils;
import aiai.ai.station.actors.DownloadSnippetActor;
import aiai.ai.station.tasks.DownloadSnippetTask;
import aiai.ai.yaml.metadata.Metadata;
import aiai.ai.yaml.station_task.StationTask;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import metaheuristic.api.v1.EnumsApi;
import metaheuristic.api.v1.data.TaskApiData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Profile("station")
public class TaskAssetPreparer {

    private final Globals globals;
    private final DownloadSnippetActor downloadSnippetActor;
    private final CurrentExecState currentExecState;
    private final StationTaskService stationTaskService;
    private final LaunchpadLookupExtendedService launchpadLookupExtendedService;
    private final MetadataService metadataService;
    private final StationService stationService;

    public TaskAssetPreparer(Globals globals, DownloadSnippetActor downloadSnippetActor, CurrentExecState currentExecState, StationTaskService stationTaskService, LaunchpadLookupExtendedService launchpadLookupExtendedService, MetadataService metadataService, StationService stationService) {
        this.globals = globals;
        this.downloadSnippetActor = downloadSnippetActor;
        this.currentExecState = currentExecState;
        this.stationTaskService = stationTaskService;
        this.launchpadLookupExtendedService = launchpadLookupExtendedService;
        this.metadataService = metadataService;
        this.stationService = stationService;
    }

    public void fixedDelay() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }

        List<StationTask> tasks = stationTaskService.findAllByFinishedOnIsNullAndAssetsPreparedIs(false);
        if (tasks.size()>1) {
            log.warn("#951.01 There is more than one task: {}", tasks.stream().map(StationTask::getTaskId).collect(Collectors.toList()));
        }
        for (StationTask task : tasks) {
            if (StringUtils.isBlank(task.launchpadUrl)) {
                log.error("launchpadUrl for task {} is blank", task.getTaskId());
                continue;
            }
            if (StringUtils.isBlank(task.getParams())) {
                log.error("Params for task {} is blank", task.getTaskId());
                continue;
            }
            Metadata.LaunchpadInfo launchpadCode = metadataService.launchpadUrlAsCode(task.launchpadUrl);

            if (EnumsApi.WorkbookExecState.DOESNT_EXIST == currentExecState.getState(task.launchpadUrl, task.workbookId)) {
                stationTaskService.delete(task.launchpadUrl, task.taskId);
                log.info("Deleted orphan task {}", task);
                continue;
            }
            final TaskApiData.TaskParamYaml taskParamYaml = TaskParamYamlUtils.toTaskYaml(task.getParams());
            if (taskParamYaml.inputResourceCodes.isEmpty()) {
                log.warn("taskParamYaml.inputResourceCodes is empty\n{}", task.getParams());
                continue;
            }
            final LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad =
                    launchpadLookupExtendedService.lookupExtendedMap.get(task.launchpadUrl);

            File taskDir = stationTaskService.prepareTaskDir(launchpadCode, task.taskId);

            StationService.ResultOfChecking resultOfChecking = stationService.checkForPreparingOfAssets(task, launchpadCode, taskParamYaml, launchpad, taskDir);
            if (resultOfChecking.isError) {
                continue;
            }
            boolean isAllLoaded = resultOfChecking.isAllLoaded;

            File snippetDir = stationTaskService.prepareSnippetDir(launchpadCode);
            if (taskParamYaml.snippet.sourcing==EnumsApi.SnippetSourcing.launchpad) {
                AssetFile assetFile = ResourceUtils.prepareSnippetFile(snippetDir, taskParamYaml.snippet.getCode(), taskParamYaml.snippet.file);
                if (assetFile.isError || !assetFile.isContent) {
                    isAllLoaded = false;
                    DownloadSnippetTask snippetTask = new DownloadSnippetTask(taskParamYaml.snippet.getCode(), taskParamYaml.snippet.file, taskParamYaml.snippet.checksum, snippetDir, task.getTaskId());
                    snippetTask.launchpad = launchpad.launchpadLookup;
                    snippetTask.stationId = launchpadCode.stationId;
                    downloadSnippetActor.add(snippetTask);
                }
            }
            if (isAllLoaded) {
                log.info("All assets were prepared for task #{}, launchpad: {}", task.taskId, task.launchpadUrl);
                stationTaskService.markAsAssetPrepared(task.launchpadUrl, task.taskId, true);
            }
        }
    }
}

/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.station;

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.station.actors.DownloadResourceActor;
import aiai.ai.station.actors.DownloadSnippetActor;
import aiai.ai.station.tasks.DownloadResourceTask;
import aiai.ai.station.tasks.DownloadSnippetTask;
import aiai.ai.utils.CollectionUtils;
import aiai.ai.yaml.metadata.Metadata;
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import aiai.ai.yaml.station.StationTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
@Slf4j
@Profile("station")
public class TaskAssetPreparer {

    private final Globals globals;
    private final DownloadSnippetActor downloadSnippetActor;
    private final DownloadResourceActor downloadResourceActor;
    private final TaskParamYamlUtils taskParamYamlUtils;
    private final CurrentExecState currentExecState;
    private final StationTaskService stationTaskService;
    private final LaunchpadLookupExtendedService launchpadLookupExtendedService;
    private final MetadataService metadataService;

    public TaskAssetPreparer(Globals globals, DownloadSnippetActor downloadSnippetActor, DownloadResourceActor downloadResourceActor, TaskParamYamlUtils taskParamYamlUtils, CurrentExecState currentExecState, StationTaskService stationTaskService, LaunchpadLookupExtendedService launchpadLookupExtendedService, MetadataService metadataService) {
        this.globals = globals;
        this.downloadSnippetActor = downloadSnippetActor;
        this.downloadResourceActor = downloadResourceActor;
        this.taskParamYamlUtils = taskParamYamlUtils;
        this.currentExecState = currentExecState;
        this.stationTaskService = stationTaskService;
        this.launchpadLookupExtendedService = launchpadLookupExtendedService;
        this.metadataService = metadataService;
    }

    public void fixedDelay() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }

        List<StationTask> tasks = stationTaskService.findAllByFinishedOnIsNullAndAssetsPreparedIs(false);
/*
        if (log.isDebugEnabled() && !tasks.isEmpty()) {
            log.debug("There are task(s) for processing:");
            for (StationTask task : tasks) {
                log.debug("\t{}", task);
            }
        }
*/
        for (StationTask task : tasks) {
            if (StringUtils.isBlank(task.launchpadUrl)) {
                log.error("launchpadUrl for task {} is blank", task.getTaskId());
                continue;
            }
            if (StringUtils.isBlank(task.getParams())) {
                log.error("Params for task {} is blank", task.getTaskId());
                continue;
            }
            Metadata.LaunchpadCode launchpadCode = metadataService.launchpadUrlAsCode(task.launchpadUrl);

            if (Enums.FlowInstanceExecState.DOESNT_EXIST == currentExecState.getState(task.launchpadUrl, task.flowInstanceId)) {
                stationTaskService.delete(task.launchpadUrl, task.taskId);
                log.info("Deleted orphan task {}", task);
                continue;
            }
            final TaskParamYaml taskParamYaml = taskParamYamlUtils.toTaskYaml(task.getParams());
            if (taskParamYaml.inputResourceCodes.isEmpty()) {
                log.warn("taskParamYaml.inputResourceCodes is empty\n{}", task.getParams());
                continue;
            }
            final LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad =
                    launchpadLookupExtendedService.lookupExtendedMap.get(task.launchpadUrl);

            File taskDir = stationTaskService.prepareTaskDir(launchpadCode, task.taskId);

            boolean isAllLoaded = true;
            for (String resourceCode : CollectionUtils.toPlainList(taskParamYaml.inputResourceCodes.values())) {
                AssetFile assetFile = StationResourceUtils.prepareDataFile(taskDir, resourceCode, null);
                // is this resource prepared?
                if (!assetFile.isError && assetFile.isContent) {
                    continue;
                }
                isAllLoaded=false;
                DownloadResourceTask resourceTask = new DownloadResourceTask(resourceCode, task.getTaskId(), taskDir);
                resourceTask.launchpad = launchpad.launchpadLookup;
                downloadResourceActor.add(resourceTask);
            }

            File snippetDir = stationTaskService.prepareSnippetDir(launchpadCode);
            if (!taskParamYaml.snippet.fileProvided) {
                AssetFile assetFile = StationResourceUtils.prepareSnippetFile(snippetDir, taskParamYaml.snippet.code, taskParamYaml.snippet.filename);
                if (assetFile.isError || !assetFile.isContent) {
                    isAllLoaded = false;
                    DownloadSnippetTask snippetTask = new DownloadSnippetTask(taskParamYaml.snippet.code, taskParamYaml.snippet.filename, taskParamYaml.snippet.checksum, snippetDir);
                    snippetTask.launchpad = launchpad.launchpadLookup;
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

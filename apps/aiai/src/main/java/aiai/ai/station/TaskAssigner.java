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
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import aiai.ai.yaml.station.StationTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
@Slf4j
public class TaskAssigner {

    private final Globals globals;
    private final DownloadSnippetActor downloadSnippetActor;
    private final DownloadResourceActor downloadResourceActor;
    private final TaskParamYamlUtils taskParamYamlUtils;
    private final CurrentExecState currentExecState;
    private final StationTaskService stationTaskService;
    private final StationService stationService;

    public TaskAssigner(Globals globals, DownloadSnippetActor downloadSnippetActor, DownloadResourceActor downloadResourceActor, TaskParamYamlUtils taskParamYamlUtils, CurrentExecState currentExecState, StationTaskService stationTaskService, StationService stationService) {
        this.globals = globals;
        this.downloadSnippetActor = downloadSnippetActor;
        this.downloadResourceActor = downloadResourceActor;
        this.taskParamYamlUtils = taskParamYamlUtils;
        this.currentExecState = currentExecState;
        this.stationTaskService = stationTaskService;
        this.stationService = stationService;
    }

    public void fixedDelay() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }

        List<StationTask> tasks = stationTaskService.findAllByFinishedOnIsNull();
        if (log.isInfoEnabled()) {
            log.info("There are task(s) for processing:");
            for (StationTask task : tasks) {
                log.info("\t{}", task);
            }
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
            if (Enums.FlowInstanceExecState.DOESNT_EXIST==currentExecState.getState(task.flowInstanceId)) {
                stationTaskService.delete(task.taskId);
                log.info("Deleted orphan task {}", task);
                continue;
            }
            final TaskParamYaml taskParamYaml = taskParamYamlUtils.toTaskYaml(task.getParams());
            if (taskParamYaml.inputResourceCodes.isEmpty()) {
                log.warn("taskParamYaml.inputResourceCodes is empty\n{}", task.getParams());
                continue;
            }
            final StationService.LaunchpadLookupExtended launchpad = stationService.lookupExtendedMap.get(task.launchpadUrl);

            File taskDir = stationTaskService.prepareTaskDir(task.taskId);

            for (String code : CollectionUtils.toPlainList(taskParamYaml.inputResourceCodes.values())) {
                DownloadResourceTask resourceTask = new DownloadResourceTask(code, taskDir);
                resourceTask.launchpad = launchpad.launchpadLookup;
                downloadResourceActor.add(resourceTask);
            }
            if (!taskParamYaml.snippet.fileProvided) {
                DownloadSnippetTask snippetTask = new DownloadSnippetTask(taskParamYaml.snippet.code, taskParamYaml.snippet.filename, taskParamYaml.snippet.checksum, taskDir);
                snippetTask.launchpad = launchpad.launchpadLookup;
                downloadSnippetActor.add(snippetTask);
            }
        }
    }
}

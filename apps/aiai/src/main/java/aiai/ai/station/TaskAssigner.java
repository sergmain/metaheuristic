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

import aiai.ai.Globals;
import aiai.ai.station.actors.DownloadResourceActor;
import aiai.ai.station.actors.DownloadSnippetActor;
import aiai.ai.station.tasks.DownloadResourceTask;
import aiai.ai.station.tasks.DownloadSnippetTask;
import aiai.ai.yaml.sequence.SimpleResource;
import aiai.ai.yaml.sequence.SimpleSnippet;
import aiai.ai.yaml.sequence.TaskParamYaml;
import aiai.ai.yaml.sequence.TaskParamYamlUtils;
import aiai.ai.yaml.station.StationTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

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

    public TaskAssigner(Globals globals, DownloadSnippetActor downloadSnippetActor, DownloadResourceActor downloadResourceActor, TaskParamYamlUtils taskParamYamlUtils, CurrentExecState currentExecState, StationTaskService stationTaskService) {
        this.globals = globals;
        this.downloadSnippetActor = downloadSnippetActor;
        this.downloadResourceActor = downloadResourceActor;
        this.taskParamYamlUtils = taskParamYamlUtils;
        this.currentExecState = currentExecState;
        this.stationTaskService = stationTaskService;
    }

    public void fixedDelay() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }

        List<StationTask> tasks = stationTaskService.findAllByFinishedOnIsNull();
        for (StationTask task : tasks) {
            if (StringUtils.isBlank(task.getParams())) {
                log.error("Params for task {} is blank", task.getTaskId());
                continue;
            }
            if (currentExecState.isInit && currentExecState.getState(task.taskId)==null) {
                stationTaskService.delete(task);
                log.info("Deleted orphan task {}", task);
                continue;
            }
            final TaskParamYaml taskParamYaml = taskParamYamlUtils.toTaskYaml(task.getParams());
            if (taskParamYaml.resources.isEmpty()) {
                log.warn("taskParamYaml.resources is empty\n{}", task.getParams());
                continue;
            }

            for (SimpleResource resource : taskParamYaml.resources) {
                downloadResourceActor.add(new DownloadResourceTask(resource.id, resource.binaryDataType));
            }
            for (SimpleSnippet snippet : taskParamYaml.getSnippets()) {
                downloadSnippetActor.add(new DownloadSnippetTask(snippet.code, snippet.filename, snippet.checksum));
            }
        }
    }
}

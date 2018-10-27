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
import aiai.ai.launchpad.beans.BinaryData;
import aiai.ai.station.actors.DownloadResourceActor;
import aiai.ai.station.tasks.DownloadResourceTask;
import aiai.ai.yaml.station.StationTask;
import aiai.ai.station.actors.DownloadDatasetActor;
import aiai.ai.station.actors.DownloadFeatureActor;
import aiai.ai.station.actors.DownloadSnippetActor;
import aiai.ai.station.tasks.DownloadFeatureTask;
import aiai.ai.station.tasks.DownloadSnippetTask;
import aiai.ai.yaml.sequence.TaskParamYaml;
import aiai.ai.yaml.sequence.TaskParamYamlUtils;
import aiai.ai.yaml.sequence.SimpleFeature;
import aiai.ai.yaml.sequence.SimpleSnippet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class TaskAssigner {

    private final Globals globals;
    private final DownloadDatasetActor downloadDatasetActor;
    private final DownloadFeatureActor downloadFeatureActor;
    private final DownloadSnippetActor downloadSnippetActor;
    private final DownloadResourceActor downloadResourceActor;
    private final TaskParamYamlUtils taskParamYamlUtils;
    private final CurrentExecState currentExecState;
    private final StationExperimentService stationExperimentService;

    public TaskAssigner(Globals globals, DownloadDatasetActor downloadDatasetActor, DownloadFeatureActor downloadFeatureActor, DownloadSnippetActor downloadSnippetActor, DownloadResourceActor downloadResourceActor, TaskParamYamlUtils taskParamYamlUtils, CurrentExecState currentExecState, StationExperimentService stationExperimentService) {
        this.globals = globals;
        this.downloadDatasetActor = downloadDatasetActor;
        this.downloadFeatureActor = downloadFeatureActor;
        this.downloadSnippetActor = downloadSnippetActor;
        this.downloadResourceActor = downloadResourceActor;
        this.taskParamYamlUtils = taskParamYamlUtils;
        this.currentExecState = currentExecState;
        this.stationExperimentService = stationExperimentService;
    }

    public void fixedDelay() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }

        List<StationTask> seqs = stationExperimentService.findAllByFinishedOnIsNull();
        for (StationTask seq : seqs) {
            if (StringUtils.isBlank(seq.getParams())) {
                // strange behaviour. this field is required in DB and can't be null
                // is this bug in mysql or it's a spring's data bug with MEDIUMTEXT fields?
                log.warn("Params for sequence {} is blank", seq.getTaskId());
                continue;
            }
            final TaskParamYaml taskParamYaml = taskParamYamlUtils.toTaskYaml(seq.getParams());
            if (taskParamYaml.dataset==null) {
                log.warn("taskParamYaml.dataset is null\n{}", seq.getParams());
                continue;
            }

            if (currentExecState.isInit && currentExecState.getState(taskParamYaml.getExperimentId())==null) {
                stationExperimentService.delete(seq);
                log.info("Deleted orphan sequence {}", seq);
                continue;
            }

            createDownloadDatasetTask(taskParamYaml.dataset.id);
            for (SimpleFeature simpleFeature : taskParamYaml.features) {
                createDownloadFeatureTask(taskParamYaml.dataset.id, simpleFeature.id);
            }
            for (SimpleSnippet snippet : taskParamYaml.getSnippets()) {
                createDownloadSnippetTask(snippet);
            }
        }
    }

    private void createDownloadDatasetTask(long datasetId) {
//        downloadDatasetActor.add(new DownloadDatasetTask(datasetId));
        downloadResourceActor.add(new DownloadResourceTask(datasetId, BinaryData.Type.DATASET));
    }

    private void createDownloadFeatureTask(long datasetId, long featureId) {
        downloadFeatureActor.add(new DownloadFeatureTask(datasetId, featureId));
    }

    private void createDownloadSnippetTask(SimpleSnippet snippet) {
        downloadSnippetActor.add(new DownloadSnippetTask(snippet.code, snippet.filename, snippet.checksum));
    }
}

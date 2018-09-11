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
import aiai.ai.beans.StationExperimentSequence;
import aiai.ai.repositories.StationExperimentSequenceRepository;
import aiai.ai.station.actors.DownloadDatasetActor;
import aiai.ai.station.actors.DownloadFeatureActor;
import aiai.ai.station.actors.DownloadSnippetActor;
import aiai.ai.station.tasks.DownloadDatasetTask;
import aiai.ai.station.tasks.DownloadFeatureTask;
import aiai.ai.station.tasks.DownloadSnippetTask;
import aiai.ai.yaml.sequence.SequenceYaml;
import aiai.ai.yaml.sequence.SequenceYamlUtils;
import aiai.ai.yaml.sequence.SimpleFeature;
import aiai.ai.yaml.sequence.SimpleSnippet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@EnableScheduling
@Slf4j
public class TaskAssigner {

    private final Globals globals;
    private final DownloadDatasetActor downloadDatasetActor;
    private final DownloadFeatureActor downloadFeatureActor;
    private final DownloadSnippetActor downloadSnippetActor;
    private final StationExperimentSequenceRepository stationExperimentSequenceRepository;

    @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.task-assigner-task.timeout'), 3, 20, 10)*1000 }")
    public void fixedDelay() {
        log.info("TaskAssigner.fixedDelay()");

        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }

        List<StationExperimentSequence> seqs = stationExperimentSequenceRepository.findAllByFinishedOnIsNull();
        for (StationExperimentSequence seq : seqs) {
            if (seq.getParams().length()==0) {
                System.out.println("!!!!Hmmm");
                continue;
            }
            final SequenceYaml sequenceYaml = SequenceYamlUtils.toSequenceYaml(seq.getParams());
            createDownloadDatasetTask(sequenceYaml.dataset.id);
            for (SimpleFeature simpleFeature : sequenceYaml.features) {
                createDownloadFeatureTask(sequenceYaml.dataset.id, simpleFeature.id);
            }
            for (SimpleSnippet snippet : sequenceYaml.getSnippets()) {
                createDownloadSnippetTask(snippet);
            }
        }
    }

    public TaskAssigner(Globals globals, DownloadDatasetActor downloadDatasetActor, DownloadFeatureActor downloadFeatureActor, DownloadSnippetActor downloadSnippetActor, StationExperimentSequenceRepository stationExperimentSequenceRepository) {
        this.globals = globals;
        this.downloadDatasetActor = downloadDatasetActor;
        this.downloadFeatureActor = downloadFeatureActor;
        this.downloadSnippetActor = downloadSnippetActor;
        this.stationExperimentSequenceRepository = stationExperimentSequenceRepository;
    }

    private void createDownloadDatasetTask(long datasetId) {
        downloadDatasetActor.add(new DownloadDatasetTask(datasetId));
    }

    private void createDownloadFeatureTask(long datasetId, long featureId) {
        downloadFeatureActor.add(new DownloadFeatureTask(datasetId, featureId));
    }

    private void createDownloadSnippetTask(SimpleSnippet snippet) {
        downloadSnippetActor.add(new DownloadSnippetTask(snippet.code, snippet.filename, snippet.checksum));
    }
}

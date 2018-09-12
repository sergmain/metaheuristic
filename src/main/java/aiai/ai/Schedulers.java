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
package aiai.ai;

import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.station.ArtifactCleaner;
import aiai.ai.station.LaunchpadRequester;
import aiai.ai.station.SequenceProcessor;
import aiai.ai.station.TaskAssigner;
import aiai.ai.station.actors.DownloadDatasetActor;
import aiai.ai.station.actors.DownloadFeatureActor;
import aiai.ai.station.actors.DownloadSnippetActor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@EnableScheduling
public class Schedulers {

    private final LaunchpadRequester launchpadRequester;
    private final TaskAssigner taskAssigner;
    private final SequenceProcessor sequenceProcessor;
    private final DownloadSnippetActor downloadSnippetActor;
    private final DownloadFeatureActor downloadFeatureActor;
    private final DownloadDatasetActor downloadDatasetActor;
    private final ExperimentService experimentService;
    private final ArtifactCleaner artifactCleaner;

    public Schedulers(LaunchpadRequester launchpadRequester, TaskAssigner taskAssigner, SequenceProcessor sequenceProcessor, DownloadSnippetActor downloadSnippetActor, DownloadFeatureActor downloadFeatureActor, DownloadDatasetActor downloadDatasetActor, ExperimentService experimentService, ArtifactCleaner artifactCleaner) {
        this.launchpadRequester = launchpadRequester;
        this.taskAssigner = taskAssigner;
        this.sequenceProcessor = sequenceProcessor;
        this.downloadSnippetActor = downloadSnippetActor;
        this.downloadFeatureActor = downloadFeatureActor;
        this.downloadDatasetActor = downloadDatasetActor;
        this.experimentService = experimentService;
        this.artifactCleaner = artifactCleaner;
    }

    // Launchpad schedulers

    @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.create-sequence.timeout'), 10, 20, 10)*1000 }")
    public void experimentService() {
        experimentService.fixedDelayExperimentSequencesProducer();
    }

    // Station schedulers

    @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.request-launchpad.timeout'), 3, 20, 10)*1000 }")
    public void launchRequester() {
        launchpadRequester.fixedDelay();
    }

    @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.task-assigner-task.timeout'), 3, 20, 10)*1000 }")
    public void taskAssigner() {
        taskAssigner.fixedDelay();
    }

    @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.sequence-processor.timeout'), 3, 20, 10)*1000 }")
    public void sequenceProcessor() {
        sequenceProcessor.fixedDelay();
    }

    @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.download-snippet-task.timeout'), 3, 20, 10)*1000 }")
    public void downloadSnippetActor() {
        downloadSnippetActor.fixedDelay();
    }

    @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.download-feature-task.timeout'), 3, 20, 10)*1000 }")
    public void downloadFeatureActor() {
        downloadFeatureActor.fixedDelay();
    }

    @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.download-dataset-task.timeout'), 3, 20, 10)*1000 }")
    public void downloadDatasetActor() {
        downloadDatasetActor.fixedDelay();
    }

    @Scheduled(initialDelay = 6_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.artifact-cleaner.timeout'), 10, 60, 30)*1000 }")
    public void artifactCleaner() {
        artifactCleaner.fixedDelay();
    }


}

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@EnableScheduling
@Slf4j
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

    @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.timeout.create-sequence'), 10, 20, 10)*1000 }")
    public void experimentService() {
        log.info("ExperimentService.fixedDelayExperimentSequencesProducer()");
        experimentService.fixedDelayExperimentSequencesProducer();
    }

    // Station schedulers

    @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.timeout.request-launchpad'), 3, 20, 10)*1000 }")
    public void launchRequester() {
        log.info("LaunchpadRequester.fixedDelay()");
        launchpadRequester.fixedDelay();
    }

    @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.timeout.task-assigner-task'), 3, 20, 10)*1000 }")
    public void taskAssigner() {
        log.info("TaskAssigner.fixedDelay()");
        taskAssigner.fixedDelay();
    }

    @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.timeout.sequence-processor'), 3, 20, 10)*1000 }")
    public void sequenceProcessor() {
        log.info("SequenceProcessor.fixedDelay()");
        sequenceProcessor.fixedDelay();
    }

    @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.timeout.download-snippet-task'), 3, 20, 10)*1000 }")
    public void downloadSnippetActor() {
        log.info("DownloadSnippetActor.fixedDelay()");
        downloadSnippetActor.fixedDelay();
    }

    @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.timeout.download-feature-task'), 3, 20, 10)*1000 }")
    public void downloadFeatureActor() {
        log.info("DownloadSnippetActor.fixedDelay()");
        downloadFeatureActor.fixedDelay();
    }

    @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.timeout.download-dataset-task'), 3, 20, 10)*1000 }")
    public void downloadDatasetActor() {
        log.info("DownloadDatasetActor.fixedDelay()");
        downloadDatasetActor.fixedDelay();
    }

    @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.timeout.artifact-cleaner'), 10, 60, 30)*1000 }")
    public void artifactCleaner() {
        log.info("ArtifactCleaner.fixedDelay()");
        artifactCleaner.fixedDelay();
    }


}

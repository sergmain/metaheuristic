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

public class Schedulers {

    // Launchpad schedulers
    @Service
    @EnableScheduling
    public static class Scheduler1 {
        private final ExperimentService experimentService;

        public Scheduler1(ExperimentService experimentService) {
            this.experimentService = experimentService;
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.create-sequence.timeout'), 10, 20, 10)*1000 }")
        public void experimentService() {
            experimentService.fixedDelayExperimentSequencesProducer();
        }
    }

    // Station schedulers

    @Service
    @EnableScheduling
    public static class Scheduler2 {
        private final LaunchpadRequester launchpadRequester;

        public Scheduler2(LaunchpadRequester launchpadRequester) {
            this.launchpadRequester = launchpadRequester;
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.request-launchpad.timeout'), 3, 20, 10)*1000 }")
        public void launchRequester() {
            launchpadRequester.fixedDelay();
        }
    }

    @Service
    @EnableScheduling
    public static class Scheduler22 {
        private final TaskAssigner taskAssigner;

        public Scheduler22(TaskAssigner taskAssigner) {
            this.taskAssigner = taskAssigner;
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.task-assigner-task.timeout'), 3, 20, 10)*1000 }")
        public void taskAssigner() {
            taskAssigner.fixedDelay();
        }
    }

    @Service
    @EnableScheduling
    public static class Scheduler3 {
        private final SequenceProcessor sequenceProcessor;

        public Scheduler3(SequenceProcessor sequenceProcessor) {
            this.sequenceProcessor = sequenceProcessor;
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.sequence-processor.timeout'), 3, 20, 10)*1000 }")
        public void sequenceProcessor() {
            sequenceProcessor.fixedDelay();
        }
    }

    @Service
    @EnableScheduling
    public static class Scheduler4 {
        private final DownloadSnippetActor downloadSnippetActor;

        public Scheduler4(DownloadSnippetActor downloadSnippetActor) {
            this.downloadSnippetActor = downloadSnippetActor;
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.download-snippet-task.timeout'), 3, 20, 10)*1000 }")
        public void downloadSnippetActor() {
            downloadSnippetActor.fixedDelay();
        }
    }

    @Service
    @EnableScheduling
    public static class Scheduler5 {
        private final DownloadFeatureActor downloadFeatureActor;

        public Scheduler5(DownloadFeatureActor downloadFeatureActor) {
            this.downloadFeatureActor = downloadFeatureActor;
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.download-feature-task.timeout'), 3, 20, 10)*1000 }")
        public void downloadFeatureActor() {
            downloadFeatureActor.fixedDelay();
        }
    }

    @Service
    @EnableScheduling
    public static class Scheduler6 {
        private final DownloadDatasetActor downloadDatasetActor;

        public Scheduler6(DownloadDatasetActor downloadDatasetActor) {
            this.downloadDatasetActor = downloadDatasetActor;
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.download-dataset-task.timeout'), 3, 20, 10)*1000 }")
        public void downloadDatasetActor() {
            downloadDatasetActor.fixedDelay();
        }
    }

    @Service
    @EnableScheduling
    public static class Scheduler7 {
        private final ArtifactCleaner artifactCleaner;

        public Scheduler7(ArtifactCleaner artifactCleaner) {
            this.artifactCleaner = artifactCleaner;
        }

        @Scheduled(initialDelay = 6_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.artifact-cleaner.timeout'), 10, 60, 30)*1000 }")
        public void artifactCleaner() {
            artifactCleaner.fixedDelay();
        }
    }

}

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

import aiai.ai.comm.CommandProcessor;
import aiai.ai.launchpad.LaunchpadService;
import aiai.ai.station.*;
import aiai.ai.station.actors.DownloadResourceActor;
import aiai.ai.station.actors.DownloadSnippetActor;
import aiai.ai.station.actors.UploadResourceActor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;


public class Schedulers {

    @Service
    @EnableScheduling
    @Slf4j
    @Profile("launchpad")
    public static class LaunchpadSchedulers {

        private final Globals globals;
        private final LaunchpadService launchpadService;

        public LaunchpadSchedulers(Globals globals, LaunchpadService launchpadService) {
            this.globals = globals;
            this.launchpadService = launchpadService;
        }

        // Launchpad schedulers

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.timeout.process-flow-instance'), 5, 40, 5)*1000 }")
        public void markOrderAsCompleted() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isLaunchpadEnabled) {
                return;
            }
            log.info("FlowService.markOrderAsCompleted()");
            launchpadService.getFlowService().markOrderAsCompleted();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.timeout.create-all-tasks'), 5, 40, 5)*1000 }")
        public void createAllTasks() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isLaunchpadEnabled) {
                return;
            }
            log.info("FlowService.producingFlowInstances()");
            launchpadService.getFlowService().createAllTasks();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.timeout.artifact-cleaner'), 5, 40, 10)*1000 }")
        public void artifactCleanerAtLaunchpad() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isLaunchpadEnabled) {
                return;
            }
            log.info("FlowService.producingFlowInstances()");
            launchpadService.getArtifactCleanerAtLaunchpad().fixedDelay();
        }

    }

    // Station schedulers
    @Service
    @EnableScheduling
    @Slf4j
    @Profile("station")
    public static class StationSchedulers {

        private final Globals globals;
        private final TaskAssetPreparer taskAssigner;
        private final TaskProcessor taskProcessor;
        private final DownloadSnippetActor downloadSnippetActor;
        private final DownloadResourceActor downloadResourceActor;
        private final UploadResourceActor uploadResourceActor;
        private final ArtifactCleanerAtStation artifactCleaner;
        private final StationService stationService;
        private final StationTaskService stationTaskService;
        private final CommandProcessor commandProcessor;

        private final RoundRobinForLaunchpad roundRobin;
        private final Map<String, LaunchpadRequestor> launchpadRequestorMap = new HashMap<>();

        public StationSchedulers(Globals globals, TaskAssetPreparer taskAssigner, TaskProcessor taskProcessor, DownloadSnippetActor downloadSnippetActor, DownloadResourceActor downloadResourceActor, UploadResourceActor uploadResourceActor, ArtifactCleanerAtStation artifactCleaner, StationService stationService, StationTaskService stationTaskService, CommandProcessor commandProcessor) {
            this.globals = globals;
            this.taskAssigner = taskAssigner;
            this.taskProcessor = taskProcessor;
            this.downloadSnippetActor = downloadSnippetActor;
            this.downloadResourceActor = downloadResourceActor;
            this.uploadResourceActor = uploadResourceActor;
            this.artifactCleaner = artifactCleaner;
            this.stationService = stationService;
            this.stationTaskService = stationTaskService;
            this.commandProcessor = commandProcessor;

            this.roundRobin = new RoundRobinForLaunchpad(stationService.lookupExtendedMap);

            for (Map.Entry<String, StationService.LaunchpadLookupExtended> entry : stationService.lookupExtendedMap.entrySet()) {
                final StationService.LaunchpadLookupExtended launchpad = entry.getValue();
                final LaunchpadRequestor requestor = new LaunchpadRequestor(launchpad.launchpadLookup.url, globals, commandProcessor, stationTaskService, stationService);

                launchpadRequestorMap.put(launchpad.launchpadLookup.url, requestor);
            }
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.timeout.request-launchpad'), 3, 20, 10)*1000 }")
        public void launchRequester() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isStationEnabled) {
                return;
            }

            String url = roundRobin.next();
            log.info("LaunchpadRequestor.fixedDelay() for url {}", url);
            launchpadRequestorMap.get(url).fixedDelay();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.timeout.task-assigner'), 3, 20, 5)*1000 }")
        public void taskAssigner() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isStationEnabled) {
                return;
            }
            log.info("TaskAssigner.fixedDelay()");
            taskAssigner.fixedDelay();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.timeout.task-processor'), 3, 20, 10)*1000 }")
        public void taskProcessor() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isStationEnabled) {
                return;
            }
            log.info("SequenceProcessor.fixedDelay()");
            taskProcessor.fixedDelay();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.timeout.download-snippet'), 3, 20, 10)*1000 }")
        public void downloadSnippetActor() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isStationEnabled) {
                return;
            }
            log.info("DownloadSnippetActor.fixedDelay()");
            downloadSnippetActor.fixedDelay();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.timeout.download-resource'), 3, 20, 3)*1000 }")
        public void downloadResourceActor() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isStationEnabled) {
                return;
            }
            log.info("DownloadSnippetActor.fixedDelay()");
            downloadResourceActor.fixedDelay();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.timeout.upload-result-resource'), 3, 20, 5)*1000 }")
        public void uploadResourceActor() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isStationEnabled) {
                return;
            }
            log.info("UploadResourceActor.fixedDelay()");
            uploadResourceActor.fixedDelay();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.timeout.artifact-cleaner'), 10, 60, 30)*1000 }")
        public void artifactCleaner() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isStationEnabled) {
                return;
            }
            log.info("ArtifactCleaner.fixedDelay()");
            artifactCleaner.fixedDelay();
        }
    }
}

/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package ai.metaheuristic.ai;

import ai.metaheuristic.ai.comm.CommandProcessor;
import ai.metaheuristic.ai.launchpad.LaunchpadService;
import ai.metaheuristic.ai.station.*;
import ai.metaheuristic.ai.station.actors.DownloadResourceActor;
import ai.metaheuristic.ai.station.actors.DownloadSnippetActor;
import ai.metaheuristic.ai.station.actors.UploadResourceActor;
import ai.metaheuristic.ai.station.env.EnvService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


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

        private static final long TIMEOUT_BETWEEN_RECONCILIATION = TimeUnit.MINUTES.toMillis(1);
        private long prevReconciliationTime = 0L;

        /**
         * update status of all workbooks which are in 'started' state. Also, if workbook is finished, atlas will be produced
         */
        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.timeout.process-workbook'), 1, 40, 2)*1000 }")
        public void updateWorkbookStatuses() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isLaunchpadEnabled) {
                return;
            }
            log.info("Invoke WorkbookService.updateWorkbookStatuses()");
            boolean needReconciliation = false;
            try {
                if ((System.currentTimeMillis()- prevReconciliationTime)>TIMEOUT_BETWEEN_RECONCILIATION) {
                    needReconciliation = true;
                }
                launchpadService.getWorkbookSchedulerService().updateWorkbookStatuses(needReconciliation);
            } catch (InvalidDataAccessResourceUsageException e) {
                log.error("!!! need to investigate. Error while updateWorkbookStatuses()",e);
            }
            finally {
                if (needReconciliation) {
                    prevReconciliationTime = System.currentTimeMillis();
                }
            }
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.timeout.create-all-tasks'), 5, 40, 5)*1000 }")
        public void createAllTasks() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isLaunchpadEnabled) {
                return;
            }
            log.info("Invoke PlanService.producingWorkbooks()");
            launchpadService.getPlanService().createAllTasks();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.timeout.artifact-cleaner'), 30, 300, 30)*1000 }")
        public void artifactCleanerAtLaunchpad() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isLaunchpadEnabled) {
                return;
            }
            log.info("Invoke PlanService.producingWorkbooks()");
            launchpadService.getArtifactCleanerAtLaunchpad().fixedDelay();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.timeout.exteriment-finisher'), 5, 300, 10)*1000 }")
        public void experimentFinisher() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isLaunchpadEnabled) {
                return;
            }
            log.info("Invoke PlanService.producingWorkbooks()");
            launchpadService.getExperimentService().experimentFinisher();
        }

        @Scheduled(initialDelay = 1_800_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.gc-timeout'), 600, 3600*24*7, 3600)*1000 }")
        public void garbageCollectionAtLaunchpad() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isLaunchpadEnabled) {
                return;
            }
            log.warn("Invoke System.gc()");
            System.gc();
            final Runtime rt = Runtime.getRuntime();
            log.warn("Memory after GC. Free: {}, max: {}, total: {}", rt.freeMemory(), rt.maxMemory(), rt.totalMemory());
        }

    }

    // Station schedulers
    @SuppressWarnings("FieldCanBeLocal")
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
        private final MetadataService metadataService;
        private final LaunchpadLookupExtendedService launchpadLookupExtendedService;
        private final CurrentExecState currentExecState;
        private final EnvService envService;

        private final RoundRobinForLaunchpad roundRobin;
        private final Map<String, LaunchpadRequestor> launchpadRequestorMap = new HashMap<>();

        public StationSchedulers(Globals globals, TaskAssetPreparer taskAssigner, TaskProcessor taskProcessor, DownloadSnippetActor downloadSnippetActor, DownloadResourceActor downloadResourceActor, UploadResourceActor uploadResourceActor, ArtifactCleanerAtStation artifactCleaner, StationService stationService, StationTaskService stationTaskService, CommandProcessor commandProcessor, MetadataService metadataService, LaunchpadLookupExtendedService launchpadLookupExtendedService, CurrentExecState currentExecState, EnvService envService) {
            this.globals = globals;
            this.taskAssigner = taskAssigner;
            this.taskProcessor = taskProcessor;
            this.downloadSnippetActor = downloadSnippetActor;
            this.downloadResourceActor = downloadResourceActor;
            this.uploadResourceActor = uploadResourceActor;
            this.artifactCleaner = artifactCleaner;
            this.envService = envService;

            if (launchpadLookupExtendedService.lookupExtendedMap==null) {
                throw new IllegalStateException("launchpad.yaml wasn't configured");
            }
            this.roundRobin = new RoundRobinForLaunchpad(launchpadLookupExtendedService.lookupExtendedMap);
            this.metadataService = metadataService;
            this.launchpadLookupExtendedService = launchpadLookupExtendedService;
            this.currentExecState = currentExecState;

            for (Map.Entry<String, LaunchpadLookupExtendedService.LaunchpadLookupExtended> entry : launchpadLookupExtendedService.lookupExtendedMap.entrySet()) {
                final LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad = entry.getValue();
                final LaunchpadRequestor requestor = new LaunchpadRequestor(launchpad.launchpadLookup.url, globals,
                        commandProcessor, stationTaskService, stationService, this.metadataService, this
                        .currentExecState, this.launchpadLookupExtendedService);

                launchpadRequestorMap.put(launchpad.launchpadLookup.url, requestor);
            }
        }

        @Scheduled(initialDelay = 20_000, fixedDelay = 5_000)
        public void monitorHotDeployDir() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isStationEnabled) {
                return;
            }
            log.info("Run envHotDeployService.monitorHotDeployDir()");
            envService.monitorHotDeployDir();
        }

        /**
         * this scheduler is being run at the station side
         */
        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('ai.metaheuristic.station.timeout.request-launchpad'), 3, 20, 3)*1000 }")
        public void launchRequester() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isStationEnabled) {
                return;
            }

            String url = roundRobin.next();
            if (url==null) {
                log.info("Can't find any enabled launchpad");
                return;
            }
            log.info("Run launchpadRequestor.fixedDelay() for url {}", url);
            launchpadRequestorMap.get(url).proceedWithRequest();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('ai.metaheuristic.station.timeout.task-assigner'), 3, 20, 5)*1000 }")
        public void taskAssigner() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isStationEnabled) {
                return;
            }
            log.info("Run taskAssigner.fixedDelay()");
            taskAssigner.fixedDelay();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.timeout.task-processor'), 3, 20, 10)*1000 }")
        public void taskProcessor() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isStationEnabled) {
                return;
            }
            log.info("Run taskProcessor.fixedDelay()");
            taskProcessor.fixedDelay();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.timeout.download-snippet'), 3, 20, 10)*1000 }")
        public void downloadSnippetActor() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isStationEnabled) {
                return;
            }
            log.info("Run downloadSnippetActor.fixedDelay()");
            downloadSnippetActor.fixedDelay();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.timeout.download-resource'), 3, 20, 3)*1000 }")
        public void downloadResourceActor() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isStationEnabled) {
                return;
            }
            log.info("Run downloadSnippetActor.fixedDelay()");
            downloadResourceActor.fixedDelay();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.timeout.upload-result-resource'), 3, 20, 3)*1000 }")
        public void uploadResourceActor() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isStationEnabled) {
                return;
            }
            log.info("Run uploadResourceActor.fixedDelay()");
            uploadResourceActor.fixedDelay();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.timeout.artifact-cleaner'), 10, 60, 30)*1000 }")
        public void artifactCleaner() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isStationEnabled) {
                return;
            }
            log.info("Run artifactCleaner.fixedDelay()");
            artifactCleaner.fixedDelay();
        }
    }
}

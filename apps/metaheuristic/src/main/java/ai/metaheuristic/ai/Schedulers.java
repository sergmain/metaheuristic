/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

import ai.metaheuristic.ai.launchpad.ArtifactCleanerAtLaunchpad;
import ai.metaheuristic.ai.launchpad.RoundRobinForLaunchpad;
import ai.metaheuristic.ai.launchpad.batch.BatchService;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentService;
import ai.metaheuristic.ai.launchpad.source_code.SourceCodeService;
import ai.metaheuristic.ai.launchpad.replication.ReplicationService;
import ai.metaheuristic.ai.launchpad.exec_context.ExecContextSchedulerService;
import ai.metaheuristic.ai.station.*;
import ai.metaheuristic.ai.station.actors.DownloadResourceActor;
import ai.metaheuristic.ai.station.actors.DownloadSnippetActor;
import ai.metaheuristic.ai.station.actors.UploadResourceActor;
import ai.metaheuristic.ai.station.env.EnvService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;


public class Schedulers {

    @Service
    @EnableScheduling
    @Slf4j
    @Profile("launchpad")
    @RequiredArgsConstructor
    public static class LaunchpadSchedulers {

        private final Globals globals;
        private final ExecContextSchedulerService execContextSchedulerService;
        private final SourceCodeService sourceCodeService;
        private final ArtifactCleanerAtLaunchpad artifactCleanerAtLaunchpad;
        private final ExperimentService experimentService;
        private final BatchService batchService;
        private final ReplicationService replicationService;

        // Launchpad schedulers

        private static final long TIMEOUT_BETWEEN_RECONCILIATION = TimeUnit.MINUTES.toMillis(1);
        private long prevReconciliationTime = 0L;

        /**
         * update status of all execContexts which are in 'started' state. Also, if execContext is finished, atlas will be produced
         */
        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.launchpad.timeout.process-exec-context'), 1, 40, 3)*1000 }")
        public void updateExecContextStatuses() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isLaunchpadEnabled) {
                return;
            }
            log.info("Invoking ExecContextService.updateExecContextStatuses()");
            boolean needReconciliation = false;
            try {
                if ((System.currentTimeMillis()- prevReconciliationTime) > TIMEOUT_BETWEEN_RECONCILIATION) {
                    needReconciliation = true;
                }
                execContextSchedulerService.updateExecContextStatuses(needReconciliation);
            } catch (InvalidDataAccessResourceUsageException e) {
                log.error("!!! need to investigate. Error while updateExecContextStatuses()",e);
            } catch (Throwable th) {
                log.error("Error while updateExecContextStatuses()", th);
            }
            finally {
                if (needReconciliation) {
                    prevReconciliationTime = System.currentTimeMillis();
                }
            }
        }

        /**
         * update statuses of all batches if all related execContexts are finished
         */
        @Scheduled(initialDelay = 10_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.launchpad.timeout.update-batch-statuses'), 5, 60, 5)*1000 }")
        public void updateBatchStatuses() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isLaunchpadEnabled) {
                return;
            }
            log.info("Invoking batchService.updateBatchStatuses()");
            batchService.updateBatchStatuses();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.launchpad.timeout.create-all-tasks'), 5, 40, 5)*1000 }")
        public void createAllTasks() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isLaunchpadEnabled) {
                return;
            }
            log.info("Invoking sourceCodeService.createAllTasks()");
            sourceCodeService.createAllTasks();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.launchpad.timeout.artifact-cleaner'), 30, 300, 60)*1000 }")
        public void artifactCleanerAtLaunchpad() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isLaunchpadEnabled) {
                return;
            }
            log.info("Invoking artifactCleanerAtLaunchpad.fixedDelay()");
            artifactCleanerAtLaunchpad.fixedDelay();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.launchpad.timeout.exteriment-finisher'), 5, 300, 10)*1000 }")
        public void experimentFinisher() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isLaunchpadEnabled) {
                return;
            }
            log.info("Invoking experimentService.experimentFinisher()");
            experimentService.experimentFinisher();
        }

        @Scheduled(initialDelay = 1_800_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.launchpad.gc-timeout'), 600, 3600*24*7, 3600)*1000 }")
        public void garbageCollectionAtLaunchpad() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isLaunchpadEnabled) {
                return;
            }
            log.debug("Invoking System.gc()");
            System.gc();
            final Runtime rt = Runtime.getRuntime();
            log.warn("Memory after GC. Free: {}, max: {}, total: {}", rt.freeMemory(), rt.maxMemory(), rt.totalMemory());
        }

        @Scheduled(initialDelay = 23_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.launchpad.asset.sync-timeout'), 30, 3600, 120)*1000 }")
        public void syncReplication() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isLaunchpadEnabled) {
                return;
            }
            log.debug("Invoking replicationService.sync()");
            replicationService.sync();
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
        private final TaskAssetPreparer taskAssetPreparer;
        private final TaskProcessor taskProcessor;
        private final DownloadSnippetActor downloadSnippetActor;
        private final DownloadResourceActor downloadResourceActor;
        private final UploadResourceActor uploadResourceActor;
        private final ArtifactCleanerAtStation artifactCleaner;
        private final MetadataService metadataService;
        private final LaunchpadLookupExtendedService launchpadLookupExtendedService;
        private final CurrentExecState currentExecState;
        private final EnvService envService;
        private final StationCommandProcessor stationCommandProcessor;

        private final RoundRobinForLaunchpad roundRobin;
        private final Map<String, LaunchpadRequestor> launchpadRequestorMap = new HashMap<>();

        public StationSchedulers(Globals globals, TaskAssetPreparer taskAssetPreparer, TaskProcessor taskProcessor, DownloadSnippetActor downloadSnippetActor, DownloadResourceActor downloadResourceActor, UploadResourceActor uploadResourceActor, ArtifactCleanerAtStation artifactCleaner, StationService stationService, StationTaskService stationTaskService, MetadataService metadataService, LaunchpadLookupExtendedService launchpadLookupExtendedService, CurrentExecState currentExecState, EnvService envService, StationCommandProcessor stationCommandProcessor) {
            this.globals = globals;
            this.taskAssetPreparer = taskAssetPreparer;
            this.taskProcessor = taskProcessor;
            this.downloadSnippetActor = downloadSnippetActor;
            this.downloadResourceActor = downloadResourceActor;
            this.uploadResourceActor = uploadResourceActor;
            this.artifactCleaner = artifactCleaner;
            this.envService = envService;
            this.stationCommandProcessor = stationCommandProcessor;

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
                        stationTaskService, stationService, this.metadataService, this.currentExecState,
                        this.launchpadLookupExtendedService, this.stationCommandProcessor);

                launchpadRequestorMap.put(launchpad.launchpadLookup.url, requestor);
            }
        }

        @Scheduled(initialDelay = 20_000, fixedDelay = 20_000)
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
        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.station.timeout.request-launchpad'), 3, 20, 6)*1000 }")
        public void launchRequester() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isStationEnabled) {
                return;
            }

/*
            String url = roundRobin.next();
            if (url==null) {
                log.info("Can't find any enabled launchpad");
                return;
            }
*/
            Set<String> launchpads = roundRobin.getActiveLaunchpads();
            if (launchpads.isEmpty()) {
                log.info("Can't find any enabled launchpad");
                return;
            }

            for (String launchpad : launchpads) {
                log.info("Run launchpadRequestor.fixedDelay() for url {}", launchpad);
                try {
                    launchpadRequestorMap.get(launchpad).proceedWithRequest();
                } catch (Throwable th) {
                    log.error("StationSchedulers.launchRequester()", th);
                }
            }
        }

        /**
         * Prepare assets for tasks
         */
        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.station.timeout.task-assigner'), 3, 20, 5)*1000 }")
        public void taskAssigner() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isStationEnabled) {
                return;
            }
            log.info("Run taskAssigner.fixedDelay()");
            taskAssetPreparer.fixedDelay();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.station.timeout.task-processor'), 3, 20, 10)*1000 }")
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

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.station.timeout.download-snippet'), 3, 20, 10)*1000 }")
        public void downloadSnippetActor() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isStationEnabled) {
                return;
            }
            log.info("Run downloadSnippetActor.downloadSnippets()");
            downloadSnippetActor.downloadSnippets();
        }

        @Scheduled(initialDelay = 20_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.station.timeout.prepare-snippet-for-downloading'), 3, 20, 10)*1000 }")
        public void prepareSnippetForDownloading() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isStationEnabled) {
                return;
            }
            log.info("Run downloadSnippetActor.prepareSnippetForDownloading()");
            downloadSnippetActor.prepareSnippetForDownloading();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.station.timeout.download-resource'), 3, 20, 3)*1000 }")
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

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.station.timeout.upload-result-resource'), 3, 20, 3)*1000 }")
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

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.station.timeout.artifact-cleaner'), 10, 60, 30)*1000 }")
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

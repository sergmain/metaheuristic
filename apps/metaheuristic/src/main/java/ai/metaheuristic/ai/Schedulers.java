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

import ai.metaheuristic.ai.mh.dispatcher..ArtifactCleanerAtDispatcher;
import ai.metaheuristic.ai.mh.dispatcher..RoundRobinForDispatcher;
import ai.metaheuristic.ai.mh.dispatcher..batch.BatchService;
import ai.metaheuristic.ai.mh.dispatcher..experiment.ExperimentService;
import ai.metaheuristic.ai.mh.dispatcher..source_code.SourceCodeService;
import ai.metaheuristic.ai.mh.dispatcher..replication.ReplicationService;
import ai.metaheuristic.ai.mh.dispatcher..exec_context.ExecContextSchedulerService;
import ai.metaheuristic.ai.station.*;
import ai.metaheuristic.ai.station.actors.DownloadResourceActor;
import ai.metaheuristic.ai.station.actors.DownloadFunctionActor;
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
    @Profile("mh.dispatcher.")
    @RequiredArgsConstructor
    public static class LaunchpadSchedulers {

        private final Globals globals;
        private final ExecContextSchedulerService execContextSchedulerService;
        private final SourceCodeService sourceCodeService;
        private final ArtifactCleanerAtDispatcher artifactCleanerAtDispatcher;
        private final ExperimentService experimentService;
        private final BatchService batchService;
        private final ReplicationService replicationService;

        // Launchpad schedulers

        private static final long TIMEOUT_BETWEEN_RECONCILIATION = TimeUnit.MINUTES.toMillis(1);
        private long prevReconciliationTime = 0L;

        /**
         * update status of all execContexts which are in 'started' state. Also, if execContext is finished, atlas will be produced
         */
        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.mh.dispatcher..timeout.process-exec-context'), 1, 40, 3)*1000 }")
        public void updateExecContextStatuses() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.mh.dispatcher.Enabled) {
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
        @Scheduled(initialDelay = 10_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.mh.dispatcher..timeout.update-batch-statuses'), 5, 60, 5)*1000 }")
        public void updateBatchStatuses() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.mh.dispatcher.Enabled) {
                return;
            }
            log.info("Invoking batchService.updateBatchStatuses()");
            batchService.updateBatchStatuses();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.mh.dispatcher..timeout.create-all-tasks'), 5, 40, 5)*1000 }")
        public void createAllTasks() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.mh.dispatcher.Enabled) {
                return;
            }
            log.info("Invoking sourceCodeService.createAllTasks()");
            sourceCodeService.createAllTasks();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.mh.dispatcher..timeout.artifact-cleaner'), 30, 300, 60)*1000 }")
        public void artifactCleanerAtLaunchpad() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.mh.dispatcher.Enabled) {
                return;
            }
            log.info("Invoking artifactCleanerAtLaunchpad.fixedDelay()");
            artifactCleanerAtDispatcher.fixedDelay();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.mh.dispatcher..timeout.exteriment-finisher'), 5, 300, 10)*1000 }")
        public void experimentFinisher() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.mh.dispatcher.Enabled) {
                return;
            }
            log.info("Invoking experimentService.experimentFinisher()");
            experimentService.experimentFinisher();
        }

        @Scheduled(initialDelay = 1_800_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.mh.dispatcher..gc-timeout'), 600, 3600*24*7, 3600)*1000 }")
        public void garbageCollectionAtLaunchpad() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.mh.dispatcher.Enabled) {
                return;
            }
            log.debug("Invoking System.gc()");
            System.gc();
            final Runtime rt = Runtime.getRuntime();
            log.warn("Memory after GC. Free: {}, max: {}, total: {}", rt.freeMemory(), rt.maxMemory(), rt.totalMemory());
        }

        @Scheduled(initialDelay = 23_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.mh.dispatcher..asset.sync-timeout'), 30, 3600, 120)*1000 }")
        public void syncReplication() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.mh.dispatcher.Enabled) {
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
        private final DownloadFunctionActor downloadFunctionActor;
        private final DownloadResourceActor downloadResourceActor;
        private final UploadResourceActor uploadResourceActor;
        private final ArtifactCleanerAtStation artifactCleaner;
        private final MetadataService metadataService;
        private final DispatcherLookupExtendedService mh.dispatcher.LookupExtendedService;
        private final CurrentExecState currentExecState;
        private final EnvService envService;
        private final StationCommandProcessor stationCommandProcessor;

        private final RoundRobinForDispatcher roundRobin;
        private final Map<String, DispatcherRequestor> mh.dispatcher.RequestorMap = new HashMap<>();

        public StationSchedulers(Globals globals, TaskAssetPreparer taskAssetPreparer, TaskProcessor taskProcessor, DownloadFunctionActor downloadFunctionActor, DownloadResourceActor downloadResourceActor, UploadResourceActor uploadResourceActor, ArtifactCleanerAtStation artifactCleaner, StationService stationService, StationTaskService stationTaskService, MetadataService metadataService, DispatcherLookupExtendedService mh.dispatcher.LookupExtendedService, CurrentExecState currentExecState, EnvService envService, StationCommandProcessor stationCommandProcessor) {
            this.globals = globals;
            this.taskAssetPreparer = taskAssetPreparer;
            this.taskProcessor = taskProcessor;
            this.downloadFunctionActor = downloadFunctionActor;
            this.downloadResourceActor = downloadResourceActor;
            this.uploadResourceActor = uploadResourceActor;
            this.artifactCleaner = artifactCleaner;
            this.envService = envService;
            this.stationCommandProcessor = stationCommandProcessor;

            if (mh.dispatcher.LookupExtendedService.lookupExtendedMap==null) {
                throw new IllegalStateException("mh.dispatcher..yaml wasn't configured");
            }
            this.roundRobin = new RoundRobinForDispatcher(mh.dispatcher.LookupExtendedService.lookupExtendedMap);
            this.metadataService = metadataService;
            this.mh.dispatcher.LookupExtendedService = mh.dispatcher.LookupExtendedService;
            this.currentExecState = currentExecState;

            for (Map.Entry<String, DispatcherLookupExtendedService.DispatcherLookupExtended> entry : mh.dispatcher.LookupExtendedService.lookupExtendedMap.entrySet()) {
                final DispatcherLookupExtendedService.DispatcherLookupExtended mh.dispatcher. = entry.getValue();
                final DispatcherRequestor requestor = new DispatcherRequestor(mh.dispatcher..mh.dispatcher.Lookup.url, globals,
                        stationTaskService, stationService, this.metadataService, this.currentExecState,
                        this.mh.dispatcher.LookupExtendedService, this.stationCommandProcessor);

                mh.dispatcher.RequestorMap.put(mh.dispatcher..mh.dispatcher.Lookup.url, requestor);
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
        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.station.timeout.request-mh.dispatcher.'), 3, 20, 6)*1000 }")
        public void mh.dispatcher.Requester() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isStationEnabled) {
                return;
            }

/*
            String url = roundRobin.next();
            if (url==null) {
                log.info("Can't find any enabled mh.dispatcher.");
                return;
            }
*/
            Set<String> mh.dispatcher.s = roundRobin.getActiveLaunchpads();
            if (mh.dispatcher.s.isEmpty()) {
                log.info("Can't find any enabled mh.dispatcher.");
                return;
            }

            for (String mh.dispatcher. : mh.dispatcher.s) {
                log.info("Run mh.dispatcher.Requestor.proceedWithRequest() for url {}", mh.dispatcher.);
                try {
                    mh.dispatcher.RequestorMap.get(mh.dispatcher.).proceedWithRequest();
                } catch (Throwable th) {
                    log.error("StationSchedulers.mh.dispatcher.Requester()", th);
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

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.station.timeout.download-function'), 3, 20, 10)*1000 }")
        public void downloadFunctionActor() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isStationEnabled) {
                return;
            }
            log.info("Run downloadFunctionActor.downloadFunctions()");
            downloadFunctionActor.downloadFunctions();
        }

        @Scheduled(initialDelay = 20_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.station.timeout.prepare-function-for-downloading'), 3, 20, 10)*1000 }")
        public void prepareFunctionForDownloading() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isStationEnabled) {
                return;
            }
            log.info("Run downloadFunctionActor.prepareFunctionForDownloading()");
            downloadFunctionActor.prepareFunctionForDownloading();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.station.timeout.download-resource'), 3, 20, 3)*1000 }")
        public void downloadResourceActor() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.isStationEnabled) {
                return;
            }
            log.info("Run downloadResourceActor.fixedDelay()");
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

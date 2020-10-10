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

import ai.metaheuristic.ai.dispatcher.ArtifactCleanerAtDispatcher;
import ai.metaheuristic.ai.dispatcher.RoundRobinForDispatcher;
import ai.metaheuristic.ai.dispatcher.batch.BatchService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSchedulerService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.replication.ReplicationService;
import ai.metaheuristic.ai.processor.*;
import ai.metaheuristic.ai.processor.actors.DownloadFunctionService;
import ai.metaheuristic.ai.processor.actors.DownloadVariableService;
import ai.metaheuristic.ai.processor.actors.UploadVariableService;
import ai.metaheuristic.ai.processor.env.EnvService;
import ai.metaheuristic.api.EnumsApi;
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
    @Profile("dispatcher")
    @RequiredArgsConstructor
    public static class DispatcherSchedulers {

        private final Globals globals;
        private final ExecContextSchedulerService execContextSchedulerService;
        private final ArtifactCleanerAtDispatcher artifactCleanerAtDispatcher;
        private final BatchService batchService;
        private final ReplicationService replicationService;
        private final ExecContextTopLevelService execContextTopLevelService;

        // Dispatcher schedulers

        private static final long TIMEOUT_BETWEEN_RECONCILIATION = TimeUnit.MINUTES.toMillis(1);
        private long prevReconciliationTime = 0L;

        /**
         * update status of all execContexts which are in 'started' state. Also, if execContext is finished, ExperimentResult will be produced
         */
        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.timeout.process-exec-context'), 1, 40, 3)*1000 }")
        public void updateExecContextStatuses() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.dispatcherEnabled) {
                return;
            }
            // if this dispatcher is source of assets then don't do any update of execContext
            // because this dispatcher isn't processing any tasks
            if (globals.assetMode==EnumsApi.DispatcherAssetMode.source) {
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
/*
        @Scheduled(initialDelay = 10_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.timeout.update-batch-statuses'), 5, 60, 5)*1000 }")
        public void updateBatchStatuses() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.dispatcherEnabled) {
                return;
            }
            log.info("Invoking batchService.updateBatchStatuses()");
            batchService.updateBatchStatuses();
        }
*/

/*
        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.timeout.create-all-tasks'), 5, 40, 5)*1000 }")
        public void createAllTasks() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.dispatcherEnabled) {
                return;
            }
            log.info("Invoking execContextTopLevelService.createAllTasks()");
            execContextTopLevelService.createAllTasks();
        }
*/

        @Scheduled(initialDelay = 5_000, fixedDelay = 3_000)
        public void processInternalTasks() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.dispatcherEnabled) {
                return;
            }
            log.info("Invoking execContextTopLevelService.findUnassignedInternalTaskAndAssign()");
            execContextTopLevelService.findUnassignedInternalTaskAndAssign();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.timeout.artifact-cleaner'), 30, 300, 60)*1000 }")
        public void artifactCleanerAtDispatcher() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.dispatcherEnabled) {
                return;
            }
            log.info("Invoking artifactCleanerAtDispatcher.fixedDelay()");
            artifactCleanerAtDispatcher.fixedDelay();
        }

        @Scheduled(initialDelay = 1_800_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.gc-timeout'), 600, 3600*24*7, 3600)*1000 }")
        public void garbageCollectionAtDispatcher() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.dispatcherEnabled) {
                return;
            }
            log.debug("Invoking System.gc()");
            System.gc();
            final Runtime rt = Runtime.getRuntime();
            log.warn("Memory after GC. Free: {}, max: {}, total: {}", rt.freeMemory(), rt.maxMemory(), rt.totalMemory());
        }

        @Scheduled(initialDelay = 23_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.asset.sync-timeout'), 60, 3600, 120)*1000 }")
        public void syncReplication() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.dispatcherEnabled) {
                return;
            }
            log.debug("Invoking replicationService.sync()");
            replicationService.sync();
        }
    }

    // Processor schedulers
    @SuppressWarnings("FieldCanBeLocal")
    @Service
    @EnableScheduling
    @Slf4j
    @Profile("processor")
    public static class ProcessorSchedulers {

        private final Globals globals;
        private final TaskAssetPreparer taskAssetPreparer;
        private final TaskProcessor taskProcessor;
        private final DownloadFunctionService downloadFunctionActor;
        private final DownloadVariableService downloadResourceActor;
        private final UploadVariableService uploadResourceActor;
        private final ArtifactCleanerAtProcessor artifactCleaner;
        private final MetadataService metadataService;
        private final DispatcherLookupExtendedService dispatcherLookupExtendedService;
        private final CurrentExecState currentExecState;
        private final EnvService envService;
        private final ProcessorCommandProcessor processorCommandProcessor;

        private final RoundRobinForDispatcher roundRobin;
        private final Map<String, DispatcherRequestor> dispatcherRequestorMap = new HashMap<>();

        public ProcessorSchedulers(Globals globals, TaskAssetPreparer taskAssetPreparer, TaskProcessor taskProcessor, DownloadFunctionService downloadFunctionActor, DownloadVariableService downloadResourceActor, UploadVariableService uploadResourceActor, ArtifactCleanerAtProcessor artifactCleaner, ProcessorService processorService, ProcessorTaskService processorTaskService, MetadataService metadataService, DispatcherLookupExtendedService dispatcherLookupExtendedService, CurrentExecState currentExecState, EnvService envService, ProcessorCommandProcessor processorCommandProcessor) {
            this.globals = globals;
            this.taskAssetPreparer = taskAssetPreparer;
            this.taskProcessor = taskProcessor;
            this.downloadFunctionActor = downloadFunctionActor;
            this.downloadResourceActor = downloadResourceActor;
            this.uploadResourceActor = uploadResourceActor;
            this.artifactCleaner = artifactCleaner;
            this.envService = envService;
            this.processorCommandProcessor = processorCommandProcessor;

            if (dispatcherLookupExtendedService.lookupExtendedMap==null) {
                throw new IllegalStateException("dispatcher.yaml wasn't configured");
            }
            this.roundRobin = new RoundRobinForDispatcher(dispatcherLookupExtendedService.lookupExtendedMap);
            this.metadataService = metadataService;
            this.dispatcherLookupExtendedService = dispatcherLookupExtendedService;
            this.currentExecState = currentExecState;

            for (Map.Entry<String, DispatcherLookupExtendedService.DispatcherLookupExtended> entry : dispatcherLookupExtendedService.lookupExtendedMap.entrySet()) {
                final DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher = entry.getValue();
                final DispatcherRequestor requestor = new DispatcherRequestor(dispatcher.dispatcherLookup.url, globals,
                        processorTaskService, processorService, this.metadataService, this.currentExecState,
                        this.dispatcherLookupExtendedService, this.processorCommandProcessor);

                dispatcherRequestorMap.put(dispatcher.dispatcherLookup.url, requestor);
            }
        }

        @Scheduled(initialDelay = 20_000, fixedDelay = 20_000)
        public void monitorHotDeployDir() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.processorEnabled) {
                return;
            }
            log.info("Run envHotDeployService.monitorHotDeployDir()");
            envService.monitorHotDeployDir();
        }

        /**
         * this scheduler is being run at the processor side
         */
        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.processor.timeout.request-dispatcher'), 3, 20, 6)*1000 }")
        public void dispatcherRequester() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.processorEnabled) {
                return;
            }

/*
            String url = roundRobin.next();
            if (url==null) {
                log.info("Can't find any enabled dispatcher");
                return;
            }
*/
            Set<String> dispatchers = roundRobin.getActiveDispatchers();
            if (dispatchers.isEmpty()) {
                log.info("Can't find any enabled dispatcher");
                return;
            }

            for (String dispatcher : dispatchers) {
                log.info("Run dispatcherRequestor.proceedWithRequest() for url {}", dispatcher);
                try {
                    dispatcherRequestorMap.get(dispatcher).proceedWithRequest();
                } catch (Throwable th) {
                    log.error("ProcessorSchedulers.dispatcherRequester()", th);
                }
            }
        }

        /**
         * Prepare assets for tasks
         */
        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.processor.timeout.task-assigner'), 3, 20, 5)*1000 }")
        public void taskAssigner() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.processorEnabled) {
                return;
            }
            log.info("Run taskAssigner.fixedDelay()");
            taskAssetPreparer.fixedDelay();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.processor.timeout.task-processor'), 3, 20, 10)*1000 }")
        public void taskProcessor() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.processorEnabled) {
                return;
            }
            log.info("Run taskProcessor.fixedDelay()");
            taskProcessor.fixedDelay();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.processor.timeout.download-function'), 3, 20, 10)*1000 }")
        public void downloadFunctionActor() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.processorEnabled) {
                return;
            }
            log.info("Run downloadFunctionActor.downloadFunctions()");
            downloadFunctionActor.downloadFunctions();
        }

        @Scheduled(initialDelay = 20_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.processor.timeout.prepare-function-for-downloading'), 3, 20, 10)*1000 }")
        public void prepareFunctionForDownloading() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.processorEnabled) {
                return;
            }
            log.info("Run downloadFunctionActor.prepareFunctionForDownloading()");
            downloadFunctionActor.prepareFunctionForDownloading();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.processor.timeout.download-resource'), 3, 20, 3)*1000 }")
        public void downloadResourceActor() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.processorEnabled) {
                return;
            }
            log.info("Run downloadResourceActor.fixedDelay()");
            downloadResourceActor.fixedDelay();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.processor.timeout.upload-result-resource'), 3, 20, 3)*1000 }")
        public void uploadResourceActor() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.processorEnabled) {
                return;
            }
            log.info("Run uploadResourceActor.fixedDelay()");
            uploadResourceActor.fixedDelay();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.processor.timeout.artifact-cleaner'), 10, 60, 30)*1000 }")
        public void artifactCleaner() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.processorEnabled) {
                return;
            }
            log.info("Run artifactCleaner.fixedDelay()");
            artifactCleaner.fixedDelay();
        }
    }
}

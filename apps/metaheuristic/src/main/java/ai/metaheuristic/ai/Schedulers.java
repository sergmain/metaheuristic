/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.batch.BatchService;
import ai.metaheuristic.ai.dispatcher.commons.ArtifactCleanerAtDispatcher;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSchedulerService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateTopLevelService;
import ai.metaheuristic.ai.dispatcher.replication.ReplicationService;
import ai.metaheuristic.ai.dispatcher.task.TaskCheckCachingTopLevelService;
import ai.metaheuristic.ai.dispatcher.thread.DeadLockDetector;
import ai.metaheuristic.ai.processor.*;
import ai.metaheuristic.ai.processor.actors.DownloadFunctionService;
import ai.metaheuristic.ai.processor.actors.DownloadVariableService;
import ai.metaheuristic.ai.processor.actors.GetDispatcherContextInfoService;
import ai.metaheuristic.ai.processor.actors.UploadVariableService;
import ai.metaheuristic.ai.processor.dispatcher_selection.ActiveDispatchers;
import ai.metaheuristic.ai.processor.env.EnvService;
import ai.metaheuristic.ai.processor.event.KeepAliveEvent;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class Schedulers {

    @Service
    @EnableScheduling
    @Slf4j
    @Profile("dispatcher")
    @RequiredArgsConstructor
    public static class DispatcherSchedulers {

        private final Globals globals;
        private final BatchService batchService;
        private final ExecContextSchedulerService execContextSchedulerService;
        private final ArtifactCleanerAtDispatcher artifactCleanerAtDispatcher;
        private final ReplicationService replicationService;
        private final ExecContextTopLevelService execContextTopLevelService;
        private final ExecContextVariableStateTopLevelService execContextVariableStateTopLevelService;
        private final TaskCheckCachingTopLevelService taskCheckCachingTopLevelService;
        private final ExecContextTaskStateTopLevelService execContextTaskStateTopLevelService;

        // Dispatcher schedulers

        /**
         * update status of all execContexts which are in 'started' state
         */
        @Scheduled(initialDelay = 63_000, fixedDelay = 63_0000 )
        public void updateExecContextStatuses() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.dispatcherEnabled) {
                return;
            }
            // if this dispatcher is the source of assets then don't do any update of execContext
            // because this dispatcher isn't processing any tasks
            if (globals.assetMode==EnumsApi.DispatcherAssetMode.source) {
                return;
            }

            log.info("Invoking ExecContextService.updateExecContextStatuses()");
            try {
                execContextSchedulerService.updateExecContextStatuses();
            } catch (InvalidDataAccessResourceUsageException e) {
                log.error("!!! need to investigate. Error while updateExecContextStatuses()",e);
            } catch (Throwable th) {
                log.error("Error while updateExecContextStatuses()", th);
            }
        }

        /**
         * update statuses of all batches if all related execContexts are finished
         */
        @Scheduled(initialDelay = 10_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.timeout.update-batch-statuses'), 5, 60, 5)*1000 }")
        public void updateBatchStatuses() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.dispatcherEnabled) {
                return;
            }
            if (globals.assetMode==EnumsApi.DispatcherAssetMode.source) {
                return;
            }
            log.info("Invoking batchService.updateBatchStatuses()");
            batchService.updateBatchStatuses();
        }

        @Scheduled(initialDelay = 5_000, fixedDelay = 10_000)
        public void processInternalTasks() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.dispatcherEnabled) {
                return;
            }
            if (globals.assetMode==EnumsApi.DispatcherAssetMode.source) {
                return;
            }
            MetaheuristicThreadLocal.setSchedule();
            log.info("Invoking execContextTopLevelService.findUnassignedTasksAndRegisterInQueue()");
            execContextTopLevelService.findUnassignedTasksAndRegisterInQueue();
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

        @Scheduled(initialDelay = 120_000, fixedDelay = 600_000)
        public void artifactCleanerForExecContextRelativesAtDispatcher() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.dispatcherEnabled) {
                return;
            }
            log.info("Invoking artifactCleanerAtDispatcher.fixedDelayExecContextRelatives()");
            artifactCleanerAtDispatcher.fixedDelayExecContextRelatives();
        }

        @Scheduled(initialDelay = 20_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.gc-timeout'), 600, 3600*24*7, 3600)*1000 }")
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

        @Scheduled(initialDelay = 10_000, fixedDelay = 5_000 )
        public void deadlockDetector() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.dispatcherEnabled) {
                return;
            }
            DeadLockDetector.findDeadLocks();
        }

        @Scheduled(initialDelay = 10_000, fixedDelay = 10_000 )
        public void processFlushing() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.dispatcherEnabled) {
                return;
            }
            execContextVariableStateTopLevelService.processFlushing();
        }

        @Scheduled(initialDelay = 15_000, fixedDelay = 5_000 )
        public void processCheckCaching() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.dispatcherEnabled) {
                return;
            }
            taskCheckCachingTopLevelService.checkCaching();
        }

        @Scheduled(initialDelay = 15_000, fixedDelay = 5_000 )
        public void processUpdateTaskExecStatesInGraph() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.dispatcherEnabled) {
                return;
            }
            execContextTaskStateTopLevelService.processUpdateTaskExecStatesInGraph();
        }
    }

    // Processor schedulers
    @SuppressWarnings({"FieldCanBeLocal", "DuplicatedCode"})
    @Service
    @EnableScheduling
    @Slf4j
    @Profile("processor")
    @RequiredArgsConstructor
//    @DependsOn({"ai.metaheuristic.ai.processor.DispatcherLookupExtendedService"})
    public static class ProcessorSchedulers {

        private final Globals globals;
        private final TaskAssetPreparer taskAssetPreparer;
        private final TaskProcessorCoordinatorService taskProcessor;
        private final DownloadFunctionService downloadFunctionActor;
        private final DownloadVariableService downloadResourceActor;
        private final UploadVariableService uploadResourceActor;
        private final GetDispatcherContextInfoService getDispatcherContextInfoService;
        private final ArtifactCleanerAtProcessor artifactCleaner;
        private final EnvService envService;
        private final DispatcherRequestorHolderService dispatcherRequestorHolderService;
        private final ApplicationEventPublisher eventPublisher;
        private final DispatcherLookupExtendedService dispatcherLookupExtendedService;

        private ActiveDispatchers activeDispatchers;

        @PostConstruct
        public void post() {
            if (dispatcherLookupExtendedService.lookupExtendedMap==null) {
                throw new IllegalStateException("dispatcher.yaml wasn't configured");
            }
            this.activeDispatchers = new ActiveDispatchers(dispatcherLookupExtendedService.lookupExtendedMap, "ActiveDispatchers for scheduler", Enums.DispatcherSelectionStrategy.priority);
        }

        @Scheduled(initialDelay = 4_000, fixedDelay = 20_000)
        public void keepAlive() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.processorEnabled) {
                return;
            }
            log.info("Send keepAliveEvent");
            eventPublisher.publishEvent(new KeepAliveEvent());
        }

        // TODO 2020-11-20 need to decide to is a hot-deploy needed or not?
        // TODO 2020-12-30 no need it any more. leave it here in case we need to restore hot deploy
/*
        @Scheduled(initialDelay = 20_000, fixedDelay = 20_000)
        public void monitorHotDeployDir() {
            if (true) {
                return;
            }
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.processorEnabled) {
                return;
            }
            log.info("Run envHotDeployService.monitorHotDeployDir()");
            envService.monitorHotDeployDir();
        }
*/

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

            Map<ProcessorAndCoreData.DispatcherUrl, AtomicBoolean> dispatchers = activeDispatchers.getActiveDispatchers();
            if (dispatchers.isEmpty()) {
                log.info("Can't find any enabled dispatcher");
                return;
            }

            for (ProcessorAndCoreData.DispatcherUrl dispatcher : dispatchers.keySet()) {
                log.info("Run dispatcherRequestor.proceedWithRequest() for url {}", dispatcher);
                try {
                    dispatcherRequestorHolderService.dispatcherRequestorMap.get(dispatcher).dispatcherRequestor.proceedWithRequest();
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
            log.info("Run downloadFunctionActor.process()");
            downloadFunctionActor.process();
        }

        @Scheduled(initialDelay = 20_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.processor.timeout.prepare-function-for-downloading'), 20, 60, 30)*1000 }")
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
            log.info("Run downloadResourceActor.process()");
            downloadResourceActor.process();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.processor.timeout.upload-result-resource'), 3, 20, 3)*1000 }")
        public void uploadResourceActor() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.processorEnabled) {
                return;
            }
            log.info("Run uploadResourceActor.process()");
            uploadResourceActor.process();
        }

        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.processor.timeout.get-dispatcher-context-info'), 10, 60, 20)*1000 }")
        public void getDispatcherContextInfoActor() {
            if (globals.isUnitTesting) {
                return;
            }
            if (!globals.processorEnabled) {
                return;
            }
            log.info("Run getDispatcherContextInfoService.process()");
            getDispatcherContextInfoService.process();
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

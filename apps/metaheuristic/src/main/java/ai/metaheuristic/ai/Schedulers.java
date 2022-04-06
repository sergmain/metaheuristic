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
import ai.metaheuristic.ai.dispatcher.event.StartProcessReadinessEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSchedulerService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextStatusService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTaskAssigningTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTaskResettingTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateTopLevelService;
import ai.metaheuristic.ai.dispatcher.long_running.LongRunningTopLevelService;
import ai.metaheuristic.ai.dispatcher.replication.ReplicationService;
import ai.metaheuristic.ai.dispatcher.task.TaskCheckCachingTopLevelService;
import ai.metaheuristic.ai.dispatcher.thread.DeadLockDetector;
import ai.metaheuristic.ai.processor.*;
import ai.metaheuristic.ai.processor.actors.DownloadFunctionService;
import ai.metaheuristic.ai.processor.actors.DownloadVariableService;
import ai.metaheuristic.ai.processor.actors.GetDispatcherContextInfoService;
import ai.metaheuristic.ai.processor.actors.UploadVariableService;
import ai.metaheuristic.ai.processor.dispatcher_selection.ActiveDispatchers;
import ai.metaheuristic.ai.processor.event.KeepAliveEvent;
import ai.metaheuristic.ai.processor.event.ProcessorEventBusService;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class Schedulers {

    @Configuration @EnableScheduling @RequiredArgsConstructor @Slf4j @SuppressWarnings("DuplicatedCode")
    @Profile("dispatcher")
    public static class ArtifactCleanerAtDispatcherSchedulingConfig implements SchedulingConfigurer {
        private final Globals globals;
        private final ArtifactCleanerAtDispatcher artifactCleanerAtDispatcher;

        @Override
        public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
            taskRegistrar.setScheduler(Executors.newSingleThreadScheduledExecutor());
            taskRegistrar.addTriggerTask( this::artifactCleanerAtDispatcher,
                    context -> {
                        Optional<Date> lastCompletionTime = Optional.ofNullable(context.lastCompletionTime());
                        Instant nextExecutionTime = lastCompletionTime.orElseGet(Date::new).toInstant().plusSeconds(globals.dispatcher.timeout.getArtifactCleaner().toSeconds());
                        return Date.from(nextExecutionTime);
                    }
            );
        }

        public void artifactCleanerAtDispatcher() {
            if (globals.testing || !globals.dispatcher.enabled) {
                return;
            }
            log.info("Invoking artifactCleanerAtDispatcher.fixedDelay()");
            artifactCleanerAtDispatcher.fixedDelay();
        }
    }

    @Configuration @EnableScheduling @RequiredArgsConstructor @Slf4j @SuppressWarnings("DuplicatedCode")
    @Profile("dispatcher")
    public static class UpdateBatchStatusesSchedulingConfig implements SchedulingConfigurer {
        private final Globals globals;
        private final BatchService batchService;

        @Override
        public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
            taskRegistrar.setScheduler(Executors.newSingleThreadScheduledExecutor());
            taskRegistrar.addTriggerTask( this::updateBatchStatuses,
                    context -> {
                        Optional<Date> lastCompletionTime = Optional.ofNullable(context.lastCompletionTime());
                        Instant nextExecutionTime = lastCompletionTime.orElseGet(Date::new).toInstant().plusSeconds(globals.dispatcher.timeout.getUpdateBatchStatuses().toSeconds());
                        return Date.from(nextExecutionTime);
                    }
            );
        }

        public void updateBatchStatuses() {
            if (globals.testing || !globals.dispatcher.enabled) {
                return;
            }
            if (globals.dispatcher.asset.mode==EnumsApi.DispatcherAssetMode.source) {
                return;
            }
            log.info("Invoking batchService.updateBatchStatuses()");
            ArtifactCleanerAtDispatcher.setBusy();
            try {
                batchService.updateBatchStatuses();
            }
            finally {
                ArtifactCleanerAtDispatcher.notBusy();
            }
        }
    }

    @Configuration @EnableScheduling @RequiredArgsConstructor @Slf4j @SuppressWarnings("DuplicatedCode")
    @Profile("dispatcher")
    public static class GarbageCollectionAtDispatcherSchedulingConfig implements SchedulingConfigurer {
        private final Globals globals;

        @Override
        public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
            taskRegistrar.setScheduler(Executors.newSingleThreadScheduledExecutor());
            taskRegistrar.addTriggerTask( this::garbageCollectionAtDispatcher,
                    context -> {
                        Optional<Date> lastCompletionTime = Optional.ofNullable(context.lastCompletionTime());
                        Instant nextExecutionTime = lastCompletionTime.orElseGet(Date::new).toInstant().plusSeconds(globals.dispatcher.timeout.getGc().toSeconds());
                        return Date.from(nextExecutionTime);
                    }
            );
        }

        public void garbageCollectionAtDispatcher() {
            if (globals.testing || !globals.dispatcher.enabled) {
                return;
            }
            log.debug("Invoking System.gc()");
            System.gc();
            final Runtime rt = Runtime.getRuntime();
            log.warn("Memory after GC. Free: {}, max: {}, total: {}", rt.freeMemory(), rt.maxMemory(), rt.totalMemory());
        }
    }

    @Configuration @EnableScheduling @RequiredArgsConstructor @Slf4j @SuppressWarnings("DuplicatedCode")
    @Profile("dispatcher")
    public static class SyncReplicationSchedulingConfig implements SchedulingConfigurer {
        private final Globals globals;
        private final ReplicationService replicationService;

        @Override
        public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
            taskRegistrar.setScheduler(Executors.newSingleThreadScheduledExecutor());
            taskRegistrar.addTriggerTask( this::syncReplication,
                    context -> {
                        Optional<Date> lastCompletionTime = Optional.ofNullable(context.lastCompletionTime());
                        Instant nextExecutionTime = lastCompletionTime.orElseGet(Date::new).toInstant().plusSeconds(globals.dispatcher.asset.getSyncTimeout().toSeconds());
                        return Date.from(nextExecutionTime);
                    }
            );
        }

        public void syncReplication() {
            if (globals.testing || !globals.dispatcher.enabled) {
                return;
            }
            log.debug("Invoking replicationService.sync()");
            ArtifactCleanerAtDispatcher.setBusy();
            try {
                replicationService.sync();
            }
            finally {
                ArtifactCleanerAtDispatcher.notBusy();
            }
        }
    }

    @Service
    @EnableScheduling
    @Slf4j
    @Profile("dispatcher")
    @RequiredArgsConstructor
    public static class DispatcherSchedulers {

        private final Globals globals;
        private final ExecContextSchedulerService execContextSchedulerService;
        private final ExecContextVariableStateTopLevelService execContextVariableStateTopLevelService;
        private final TaskCheckCachingTopLevelService taskCheckCachingTopLevelService;
        private final ExecContextTaskStateTopLevelService execContextTaskStateTopLevelService;
        private final LongRunningTopLevelService longRunningTopLevelService;
        private final ApplicationEventPublisher eventPublisher;
        private final ExecContextStatusService execContextStatusService;
        private final ExecContextTaskResettingTopLevelService execContextTaskResettingTopLevelService;
        private final ExecContextTaskAssigningTopLevelService execContextTaskAssigningTopLevelService;

        // Dispatcher schedulers with fixed delay

        @Scheduled(initialDelay = 63_000, fixedDelay = 630_000 )
        public void updateExecContextStatuses() {
            if (globals.testing || !globals.dispatcher.enabled) {
                return;
            }
            // if this dispatcher is the source of assets then don't do any update of execContext
            // because this dispatcher isn't processing any tasks
            if (globals.dispatcher.asset.mode==EnumsApi.DispatcherAssetMode.source) {
                return;
            }

            log.info("Invoking ExecContextService.updateExecContextStatuses()");
            ArtifactCleanerAtDispatcher.setBusy();
            try {
                execContextSchedulerService.updateExecContextStatuses();
            } catch (InvalidDataAccessResourceUsageException e) {
                log.error("!!! need to investigate. Error while updateExecContextStatuses()",e);
            } catch (Throwable th) {
                log.error("Error while updateExecContextStatuses()", th);
            }
            finally {
                ArtifactCleanerAtDispatcher.notBusy();
            }
        }

        boolean needToInitializeReadyness = true;

        @Scheduled(initialDelay = 5_000, fixedDelay = 17_000)
        public void processInternalTasks() {
            if (globals.testing || !globals.dispatcher.enabled) {
                return;
            }
            if (globals.dispatcher.asset.mode==EnumsApi.DispatcherAssetMode.source) {
                return;
            }
            if (needToInitializeReadyness) {
                eventPublisher.publishEvent(new StartProcessReadinessEvent());
                needToInitializeReadyness = false;
            }
            log.warn("Invoking execContextTopLevelService.findUnassignedTasksAndRegisterInQueue()");
            ArtifactCleanerAtDispatcher.setBusy();
            try {
                execContextTaskAssigningTopLevelService.findUnassignedTasksAndRegisterInQueue();
            }
            finally {
                ArtifactCleanerAtDispatcher.notBusy();
            }
        }

        @Scheduled(initialDelay = 13_000, fixedDelay = 13_000 )
        public void deadlockDetector() {
            if (globals.testing || !globals.dispatcher.enabled) {
                return;
            }
            DeadLockDetector.findDeadLocks();
        }

        @Scheduled(initialDelay = 10_000, fixedDelay = 10_000 )
        public void processFlushing() {
            if (globals.testing || !globals.dispatcher.enabled) {
                return;
            }
            execContextVariableStateTopLevelService.processFlushing();
        }

        @Scheduled(initialDelay = 15_000, fixedDelay = 10_000 )
        public void execContextStatusUpdate() {
            if (globals.testing || !globals.dispatcher.enabled) {
                return;
            }
            execContextStatusService.resetStatus();
        }

        @Scheduled(initialDelay = 15_000, fixedDelay = 11_000 )
        public void resetTasksWithErrorForRecovery() {
            if (globals.testing || !globals.dispatcher.enabled) {
                return;
            }
            ArtifactCleanerAtDispatcher.setBusy();
            try {
                execContextTaskResettingTopLevelService.resetTasksWithErrorForRecovery();
            }
            finally {
                ArtifactCleanerAtDispatcher.notBusy();
            }
        }

        @Scheduled(initialDelay = 15_000, fixedDelay = 17_000 )
        public void processCheckCaching() {
            if (globals.testing || !globals.dispatcher.enabled) {
                return;
            }
            ArtifactCleanerAtDispatcher.setBusy();
            try {
                taskCheckCachingTopLevelService.checkCaching();
            }
            finally {
                ArtifactCleanerAtDispatcher.notBusy();
            }
        }

        @Scheduled(initialDelay = 15_000, fixedDelay = 5_000 )
        public void processUpdateTaskExecStatesInGraph() {
            if (globals.testing || !globals.dispatcher.enabled) {
                return;
            }
            ArtifactCleanerAtDispatcher.setBusy();
            try {
                execContextTaskStateTopLevelService.processUpdateTaskExecStatesInGraph();
            }
            finally {
                ArtifactCleanerAtDispatcher.notBusy();
            }
        }

        @Scheduled(initialDelay = 25_000, fixedDelay = 15_000 )
        public void updateStateForLongRunning() {
            if (globals.testing || !globals.dispatcher.enabled) {
                return;
            }
            longRunningTopLevelService.updateStateForLongRunning();
        }
    }

    @Configuration @EnableScheduling @RequiredArgsConstructor @Slf4j @SuppressWarnings("DuplicatedCode")
    @Profile("processor")
    public static class DispatcherRequesterSchedulingConfig implements SchedulingConfigurer {
        private final Globals globals;
        private ActiveDispatchers activeDispatchers;
        private final DispatcherRequestorHolderService dispatcherRequestorHolderService;
        private final DispatcherLookupExtendedService dispatcherLookupExtendedService;

        @PostConstruct
        public void post() {
            if (dispatcherLookupExtendedService.lookupExtendedMap==null) {
                throw new IllegalStateException("dispatcher.yaml wasn't configured");
            }
            this.activeDispatchers = new ActiveDispatchers(dispatcherLookupExtendedService.lookupExtendedMap, "ActiveDispatchers for scheduler", Enums.DispatcherSelectionStrategy.priority);
        }

        @Override
        public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
            taskRegistrar.setScheduler(Executors.newSingleThreadScheduledExecutor());
            taskRegistrar.addTriggerTask( this::dispatcherRequester,
                    context -> {
                        Optional<Date> lastCompletionTime = Optional.ofNullable(context.lastCompletionTime());
                        Instant nextExecutionTime = lastCompletionTime.orElseGet(Date::new).toInstant().plusSeconds(globals.processor.timeout.getRequestDispatcher().toSeconds());
                        return Date.from(nextExecutionTime);
                    }
            );
        }

        public void dispatcherRequester() {
            if (globals.testing || !globals.processor.enabled) {
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
                    // call /rest/v1/srv-v2/
                    dispatcherRequestorHolderService.dispatcherRequestorMap.get(dispatcher).dispatcherRequestor.proceedWithRequest();
                } catch (Throwable th) {
                    log.error("ProcessorSchedulers.dispatcherRequester()", th);
                }
            }
        }
    }

    @Configuration @EnableScheduling @RequiredArgsConstructor @Slf4j @SuppressWarnings("DuplicatedCode")
    @Profile("processor")
    public static class TaskAssignerSchedulingConfig implements SchedulingConfigurer {
        private final Globals globals;
        private final TaskAssetPreparer taskAssetPreparer;

        @Override
        public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
            taskRegistrar.setScheduler(Executors.newSingleThreadScheduledExecutor());
            taskRegistrar.addTriggerTask( this::taskAssigner,
                    context -> {
                        Optional<Date> lastCompletionTime = Optional.ofNullable(context.lastCompletionTime());
                        Instant nextExecutionTime = lastCompletionTime.orElseGet(Date::new).toInstant().plusSeconds(globals.processor.timeout.getTaskAssigner().toSeconds());
                        return Date.from(nextExecutionTime);
                    }
            );
        }

        // Prepare assets for tasks
//        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( globals.processor.timeout.taskAssigner.toSeconds(), 3, 20)*1000 }")
        public void taskAssigner() {
            if (globals.testing || !globals.processor.enabled) {
                return;
            }
            log.info("Run taskAssigner.fixedDelay()");
            taskAssetPreparer.fixedDelay();
        }
    }

    @Configuration @EnableScheduling @RequiredArgsConstructor @Slf4j @SuppressWarnings("DuplicatedCode")
    @Profile("processor")
    public static class TaskProcessorSchedulingConfig implements SchedulingConfigurer {
        private final Globals globals;
        private final TaskProcessorCoordinatorService taskProcessor;

        @Override
        public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
            taskRegistrar.setScheduler(Executors.newSingleThreadScheduledExecutor());
            taskRegistrar.addTriggerTask( this::taskProcessor,
                    context -> {
                        Optional<Date> lastCompletionTime = Optional.ofNullable(context.lastCompletionTime());
                        Instant nextExecutionTime = lastCompletionTime.orElseGet(Date::new).toInstant().plusSeconds(globals.processor.timeout.getTaskProcessor().toSeconds());
                        return Date.from(nextExecutionTime);
                    }
            );
        }

//        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( globals.processor.timeout.taskProcessor.toSeconds(), 3, 20)*1000 }")
        public void taskProcessor() {
            if (globals.testing || !globals.processor.enabled) {
                return;
            }
            log.info("Run taskProcessor.fixedDelay()");
            taskProcessor.fixedDelay();
        }
    }

    @Configuration @EnableScheduling @RequiredArgsConstructor @Slf4j @SuppressWarnings("DuplicatedCode")
    @Profile("processor")
    public static class DownloadFunctionActorSchedulingConfig implements SchedulingConfigurer {
        private final Globals globals;
        private final DownloadFunctionService downloadFunctionActor;

        @Override
        public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
            taskRegistrar.setScheduler(Executors.newSingleThreadScheduledExecutor());
            taskRegistrar.addTriggerTask( this::downloadFunctionActor,
                    context -> {
                        Optional<Date> lastCompletionTime = Optional.ofNullable(context.lastCompletionTime());
                        Instant nextExecutionTime = lastCompletionTime.orElseGet(Date::new).toInstant().plusSeconds(globals.processor.timeout.getDownloadFunction().toSeconds());
                        return Date.from(nextExecutionTime);
                    }
            );
        }

//        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( globals.processor.timeout.downloadFunction.toSeconds(), 3, 20)*1000 }")
        public void downloadFunctionActor() {
            if (globals.testing || !globals.processor.enabled) {
                return;
            }
            log.info("Run downloadFunctionActor.process()");
            downloadFunctionActor.process();
        }
    }

    @Configuration @EnableScheduling @RequiredArgsConstructor @Slf4j @SuppressWarnings("DuplicatedCode")
    @Profile("processor")
    public static class PrepareFunctionForDownloadingSchedulingConfig implements SchedulingConfigurer {
        private final Globals globals;
        private final DownloadFunctionService downloadFunctionActor;

        @Override
        public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
            taskRegistrar.setScheduler(Executors.newSingleThreadScheduledExecutor());
            taskRegistrar.addTriggerTask( this::prepareFunctionForDownloading,
                    context -> {
                        Optional<Date> lastCompletionTime = Optional.ofNullable(context.lastCompletionTime());
                        Instant nextExecutionTime = lastCompletionTime.orElseGet(Date::new).toInstant().plusSeconds(globals.processor.timeout.getPrepareFunctionForDownloading().toSeconds());
                        return Date.from(nextExecutionTime);
                    }
            );
        }

//        @Scheduled(initialDelay = 20_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( globals.processor.timeout.prepareFunctionForDownloading.toSeconds(), 20, 60)*1000 }")
        public void prepareFunctionForDownloading() {
            if (globals.testing || !globals.processor.enabled) {
                return;
            }
            log.info("Run downloadFunctionActor.prepareFunctionForDownloading()");
            downloadFunctionActor.prepareFunctionForDownloading();
        }

    }

    @Configuration @EnableScheduling @RequiredArgsConstructor @Slf4j @SuppressWarnings("DuplicatedCode")
    @Profile("processor")
    public static class DownloadResourceActorSchedulingConfig implements SchedulingConfigurer {
        private final Globals globals;
        private final DownloadVariableService downloadVariableService;

        @Override
        public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
            taskRegistrar.setScheduler(Executors.newSingleThreadScheduledExecutor());
            taskRegistrar.addTriggerTask( this::downloadVariableService,
                    context -> {
                        Optional<Date> lastCompletionTime = Optional.ofNullable(context.lastCompletionTime());
                        Instant nextExecutionTime = lastCompletionTime.orElseGet(Date::new).toInstant().plusSeconds(globals.processor.timeout.getDownloadResource().toSeconds());
                        return Date.from(nextExecutionTime);
                    }
            );
        }

//        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( globals.processor.timeout.downloadResource.toSeconds(), 3, 20)*1000 }")
        public void downloadVariableService() {
            if (globals.testing || !globals.processor.enabled) {
                return;
            }
            log.info("Run downloadVariableService.process()");
            downloadVariableService.process();
        }

    }

    @Configuration @EnableScheduling @RequiredArgsConstructor @Slf4j @SuppressWarnings("DuplicatedCode")
    @Profile("processor")
    public static class UploadResourceActorSchedulingConfig implements SchedulingConfigurer {
        private final Globals globals;
        private final UploadVariableService uploadResourceActor;

        @Override
        public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
            taskRegistrar.setScheduler(Executors.newSingleThreadScheduledExecutor());
            taskRegistrar.addTriggerTask( this::uploadResourceActor,
                    context -> {
                        Optional<Date> lastCompletionTime = Optional.ofNullable(context.lastCompletionTime());
                        Instant nextExecutionTime = lastCompletionTime.orElseGet(Date::new).toInstant().plusSeconds(globals.processor.timeout.getUploadResultResource().toSeconds());
                        return Date.from(nextExecutionTime);
                    }
            );
        }

//        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( globals.processor.timeout.uploadResultResource.toSeconds(), 3, 20)*1000 }")
        public void uploadResourceActor() {
            if (globals.testing || !globals.processor.enabled) {
                return;
            }
            log.info("Run uploadResourceActor.process()");
            uploadResourceActor.process();
        }

    }

    @Configuration @EnableScheduling @RequiredArgsConstructor @Slf4j @SuppressWarnings("DuplicatedCode")
    @Profile("processor")
    public static class GetDispatcherContextInfoActorSchedulingConfig implements SchedulingConfigurer {
        private final Globals globals;
        private final GetDispatcherContextInfoService getDispatcherContextInfoService;

        @Override
        public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
            taskRegistrar.setScheduler(Executors.newSingleThreadScheduledExecutor());
            taskRegistrar.addTriggerTask( this::getDispatcherContextInfoActor,
                    context -> {
                        Optional<Date> lastCompletionTime = Optional.ofNullable(context.lastCompletionTime());
                        Instant nextExecutionTime = lastCompletionTime.orElseGet(Date::new).toInstant().plusSeconds(globals.processor.timeout.getDispatcherContextInfo().toSeconds());
                        return Date.from(nextExecutionTime);
                    }
            );
        }

//        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( globals.processor.timeout.dispatcherContextInfo.toSeconds(), 10, 60)*1000 }")
        public void getDispatcherContextInfoActor() {
            if (globals.testing || !globals.processor.enabled) {
                return;
            }
            log.info("Run getDispatcherContextInfoService.process()");
            getDispatcherContextInfoService.process();
        }

    }

    @Configuration @EnableScheduling @RequiredArgsConstructor @Slf4j @SuppressWarnings("DuplicatedCode")
    @Profile("processor")
    public static class ProcessorArtifactCleanerSchedulingConfig implements SchedulingConfigurer {
        private final Globals globals;
        private final ArtifactCleanerAtProcessor artifactCleaner;

        @Override
        public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
            taskRegistrar.setScheduler(Executors.newSingleThreadScheduledExecutor());
            taskRegistrar.addTriggerTask( this::artifactCleaner,
                    context -> {
                        Optional<Date> lastCompletionTime = Optional.ofNullable(context.lastCompletionTime());
                        Instant nextExecutionTime = lastCompletionTime.orElseGet(Date::new).toInstant().plusSeconds(globals.processor.timeout.getArtifactCleaner().toSeconds());
                        return Date.from(nextExecutionTime);
                    }
            );
        }

//        @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( globals.processor.timeout.artifactCleaner.toSeconds(), 10, 60)*1000 }")
        public void artifactCleaner() {
            if (globals.testing || !globals.processor.enabled) {
                return;
            }
            log.info("Run artifactCleaner.fixedDelay()");
            artifactCleaner.fixedDelay();
        }
    }

    // Processor schedulers
    @SuppressWarnings({"FieldCanBeLocal", "DuplicatedCode"})
    @Service
    @EnableScheduling
    @Slf4j
    @Profile("processor")
    @RequiredArgsConstructor
    public static class ProcessorSchedulers {

        private final Globals globals;
        private final ProcessorEventBusService processorEventBusService;

        // this scheduler is being run at the processor side

        @Scheduled(initialDelay = 4_000, fixedDelay = 20_000)
        public void keepAlive() {
            if (globals.testing) {
                return;
            }
            if (!globals.processor.enabled) {
                return;
            }
            log.info("Send keepAliveEvent");
            processorEventBusService.keepAlive(new KeepAliveEvent());
        }
    }
}

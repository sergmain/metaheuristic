/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorCoreTask;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.utils.CleaningPolicyUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * <p>Error code prefix: {@code 01.339.} (unique to this class).
 */
@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ArtifactCleanerAtProcessor {

    private final ProcessorTaskService processorTaskService;
    private final CurrentExecState currentExecState;
    private final Globals globals;
    private final ProcessorEnvironment processorEnvironment;

    /**
     * No Task is deleted during the first 25 minutes after the start of MH (the user, 2026-11-09): a start with ~100k
     * Task dirs must not compete with the deletion of obsolete ones. Judged from the start of the JVM, so the startup
     * scan of Task dirs - ProcessorTaskService.postConstruct(), minutes long on such a box - is inside the delay.
     */
    public static final Duration STARTUP_DELAY = Duration.ofMinutes(25);

    // the start of MH - of the JVM - see isStartupDelayOver()
    private final long startedOn = ManagementFactory.getRuntimeMXBean().getStartTime();

    private final AtomicBoolean inProgress = new AtomicBoolean();

    /**
     * Starts fixedDelay() in a virtual thread of its own and returns at once - a pass over every Task with a dir may take
     * minutes, and the thread of a scheduler must not be held for that long. Nothing is started before STARTUP_DELAY has
     * passed since the start of MH, and a pass which is still running isn't started a second time.
     */
    public void startCleaning() {
        if (!isStartupDelayOver(startedOn, System.currentTimeMillis())) {
            return;
        }
        if (!inProgress.compareAndSet(false, true)) {
            log.info("01.339.040 the previous cleaning of Task dirs is still in progress");
            return;
        }
        Thread.ofVirtual().name("artifact-cleaner").start(() -> {
            try {
                fixedDelay();
            }
            catch (Throwable th) {
                log.error("01.339.060 Error while cleaning Task dirs", th);
            }
            finally {
                inProgress.set(false);
            }
        });
    }

    /**
     * Whether STARTUP_DELAY has passed between 'startedOn' and 'now', both in milliseconds. Static and free of any state
     * on purpose - it's testable without a Spring context.
     */
    public static boolean isStartupDelayOver(long startedOn, long now) {
        return now - startedOn >= STARTUP_DELAY.toMillis();
    }

    @SneakyThrows
    public void fixedDelay() {

        if (!globals.processor.enabled) {
            // don't delete anything until the processor has received the full list of actual ExecContexts
            return;
        }
        // one moment for the whole pass - the policies with a timeout are judged by it
        final long now = System.currentTimeMillis();

        for (ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core : processorEnvironment.getProcessorEnv().metadataParams().getAllEnabledRefsForCores()) {
            if (currentExecState.getCurrentInitState(core.dispatcherUrl) != Enums.ExecContextInitState.FULL) {
                // don't delete anything until the processor has received the full list of actual ExecContexts
                continue;
            }
            Path coreDir = globals.processorPath.resolve(core.coreCode);
            Path coreTaskDir = coreDir.resolve(Consts.TASK_DIR);

            MetadataParamsYaml.ProcessorSession processorState = processorEnvironment.getProcessorEnv().metadataParams().processorStateByDispatcherUrl(core);
            final Path dispatcherDir = coreTaskDir.resolve(processorState.dispatcherCode);
            if (Files.notExists(dispatcherDir)) {
                Files.createDirectories(dispatcherDir);
            }
            // every Task with a dir, including the Tasks of FINISHED ExecContexts which weren't loaded at startup
            List<ProcessorTaskService.TaskShortInfo> all = processorTaskService.findAllTaskShortInfosForCore(core);
            // the loaded Tasks, key - taskId
            final Map<Long, ProcessorCoreTask> loaded = processorTaskService.findAllForCore(core).stream()
                    .collect(Collectors.toMap(t -> t.taskId, t -> t, (t1, t2) -> t1));
            for (ProcessorTaskService.TaskShortInfo info : all) {
                if (currentExecState.isState(core.dispatcherUrl, info.execContextId(), EnumsApi.ExecContextState.DOESNT_EXIST)) {
                    log.warn("Delete obsolete task, id {}, url {}", info.id(), core.dispatcherUrl.url);
                    for (Map.Entry<EnumsApi.ExecContextState, String> entry : currentExecState.getExecContextsNormalized(core.dispatcherUrl).entrySet()) {
                        log.warn("'   {}: {}", entry.getKey(), entry.getValue());
                    }
                    processorTaskService.delete(core, info.id());
                    continue;
                }
                // a not loaded Task - of a FINISHED ExecContext - has nothing more to clean than its whole dir, as above
                final ProcessorCoreTask task = loaded.get(info.id());
                if (task == null) {
                    continue;
                }
                final boolean done = task.delivered && task.completed;
                final Set<EnumsApi.CleaningPolicy> cleaningPolicies = cleaningPoliciesOf(task);
                if (isTaskDirCleaningRequired(task.clean, cleaningPolicies, done, task.finishedOn, now)) {
                    log.info("Delete task with ((task.clean || cleaningPolicy ALL || a due FULL_AFTER_ONE_WEEK) && task.delivered && task.completed), id {}, url {}", task.getTaskId(), core.dispatcherUrl.url);
                    processorTaskService.delete(core, task.getTaskId());
                    continue;
                }
                if (isAssetDirCleaningRequired(cleaningPolicies, done, task.finishedOn, now)) {
                    processorTaskService.deleteAssetDir(core, task.getTaskId());
                }
            }
        }
    }

    /**
     * The whole dir of a task is deleted either because SourceCode declared 'clean',
     * or because Function of that task declared cleaningPolicy==ALL - both mean the very same thing.
     * Static and free of any state on purpose - it's testable without a Spring context.
     * FULL_AFTER_ONE_WEEK deletes the very same as ALL, only once a week has passed since the Task's finishedOn,
     * i.e. a policy counts when it's due at the moment 'now', see CleaningPolicyUtils.isDue().
     */
    public static boolean isTaskDirCleaningRequired(boolean clean, Set<EnumsApi.CleaningPolicy> cleaningPolicies, boolean done,
                                                    @Nullable Long finishedOn, long now) {
        return done && (clean || isDue(cleaningPolicies, EnumsApi.CleaningTarget.TASK_DIR, finishedOn, now));
    }

    /**
     * Only the 'asset' sub-dir of a task's dir is deleted, the rest of taskDir stays.
     * cleaningPolicy==ALL is intentionally not handled here, it's already covered by isTaskDirCleaningRequired().
     */
    public static boolean isAssetDirCleaningRequired(Set<EnumsApi.CleaningPolicy> cleaningPolicies, boolean done,
                                                     @Nullable Long finishedOn, long now) {
        return done && isDue(cleaningPolicies, EnumsApi.CleaningTarget.ASSET_DIR, finishedOn, now);
    }

    private static boolean isDue(Set<EnumsApi.CleaningPolicy> cleaningPolicies, EnumsApi.CleaningTarget target,
                                 @Nullable Long finishedOn, long now) {
        return cleaningPolicies.stream().anyMatch(p -> p.target==target && CleaningPolicyUtils.isDue(p, finishedOn, now));
    }

    /**
     * What is due for a Task at the moment 'now' - the decision of fixedDelay() as one value. It's what the cleaning
     * of every Task dir on disk, loaded or not, applies, see ProcessorTaskDirCleaner.
     */
    public static Enums.TaskCleaningAction cleaningAction(ProcessorCoreTask task, long now) {
        final boolean done = task.delivered && task.completed;
        final Set<EnumsApi.CleaningPolicy> cleaningPolicies = cleaningPoliciesOf(task);
        if (isTaskDirCleaningRequired(task.clean, cleaningPolicies, done, task.finishedOn, now)) {
            return Enums.TaskCleaningAction.TASK_DIR;
        }
        if (isAssetDirCleaningRequired(cleaningPolicies, done, task.finishedOn, now)) {
            return Enums.TaskCleaningAction.ASSET_DIR;
        }
        return Enums.TaskCleaningAction.NONE;
    }

    /**
     * The policies of a Task. Lenient on purpose - a name this Processor doesn't know, e.g. one sent by a newer
     * Dispatcher, is logged and skipped while the rest of the list still applies. The Dispatcher is the strict side:
     * FunctionConfigYaml.checkIntegrity() refuses an unknown name when a Function is registered.
     */
    public static Set<EnumsApi.CleaningPolicy> cleaningPoliciesOf(ProcessorCoreTask task) {
        return CleaningPolicyUtils.parse(task.cleaningPolicy,
                name -> log.warn("01.339.020 task #{} declares an unknown cleaningPolicy '{}', it's skipped", task.taskId, name));
    }
}

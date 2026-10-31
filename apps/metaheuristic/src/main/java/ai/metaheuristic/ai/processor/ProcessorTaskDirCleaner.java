/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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
import ai.metaheuristic.commons.utils.DigitUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongFunction;
import java.util.stream.Stream;

/**
 * Applies SourceCode's 'clean' and Functions' cleaning policies to every Task dir on disk, the dirs of Tasks of
 * FINISHED ExecContexts - which are never loaded, so ArtifactCleanerAtProcessor.fixedDelay() doesn't see them -
 * included. It's what applies a policy with a timeout, FULL_AFTER_ONE_WEEK, to every Task. Started every 12 hours by
 * Schedulers.ProcessorSchedulers, never as a part of the startup.
 *
 * <p>One virtual thread per group dir - the top-level part of a taskId, 41 in task/localhost-64967/41/456 - and the
 * Task dirs of a group are processed one by one inside it, not a thread per Task dir.
 *
 * <p>Error code prefix: {@code 01.337.} (unique to this class).
 */
@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ProcessorTaskDirCleaner {

    private final Globals globals;
    private final ProcessorEnvironment processorEnvironment;
    private final ProcessorTaskService processorTaskService;

    private final AtomicBoolean inProgress = new AtomicBoolean();

    /**
     * @param taskDirs Task dirs which were looked at
     * @param deletedTaskDirs Task dirs which were deleted as a whole
     * @param deletedAssetDirs Task dirs whose 'asset' sub-dir was deleted
     */
    public record ScanResult(long taskDirs, long deletedTaskDirs, long deletedAssetDirs) {
        public static final ScanResult EMPTY = new ScanResult(0, 0, 0);

        public ScanResult plus(ScanResult o) {
            return new ScanResult(taskDirs + o.taskDirs, deletedTaskDirs + o.deletedTaskDirs, deletedAssetDirs + o.deletedAssetDirs);
        }

        public static ScanResult of(Enums.TaskCleaningAction action) {
            return switch (action) {
                case NONE -> new ScanResult(1, 0, 0);
                case TASK_DIR -> new ScanResult(1, 1, 0);
                case ASSET_DIR -> new ScanResult(1, 0, 1);
            };
        }
    }

    /**
     * Starts cleanByPolicies() in a virtual thread of its own and returns at once - a scan of every Task dir may take
     * minutes, and the thread of a scheduler must not be held for that long. A scan which is still running isn't
     * started a second time.
     */
    public void startCleaningByPolicies() {
        if (!inProgress.compareAndSet(false, true)) {
            log.info("01.337.020 the previous cleaning of Task dirs by policies is still in progress");
            return;
        }
        Thread.ofVirtual().name("task-dir-cleaner").start(() -> {
            try {
                cleanByPolicies();
            }
            catch (Throwable th) {
                log.error("01.337.040 Error while cleaning Task dirs by policies", th);
            }
            finally {
                inProgress.set(false);
            }
        });
    }

    public void cleanByPolicies() {
        if (!globals.processor.enabled) {
            return;
        }
        // one moment for the whole scan - the policies with a timeout are judged by it
        final long now = System.currentTimeMillis();
        for (ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core : processorEnvironment.getProcessorEnv().metadataParams().getAllEnabledRefsForCores()) {
            final MetadataParamsYaml.ProcessorSession processorState = processorEnvironment.getProcessorEnv().metadataParams().processorStateByDispatcherUrl(core);
            final Path dispatcherDir = globals.processorPath.resolve(core.coreCode).resolve(Consts.TASK_DIR).resolve(processorState.dispatcherCode);
            final long mills = System.currentTimeMillis();
            final ScanResult r = scanDispatcherDir(dispatcherDir,
                    taskId -> processorTaskService.applyCleaning(core, taskId, task -> ArtifactCleanerAtProcessor.cleaningAction(task, now)));
            log.info("01.337.060 core {}, {}: {} Task dir(s) scanned, {} deleted, {} asset dir(s) deleted, {} ms",
                    core.coreCode, core.dispatcherUrl.url, r.taskDirs(), r.deletedTaskDirs(), r.deletedAssetDirs(), System.currentTimeMillis() - mills);
        }
    }

    /**
     * Scans the dir of a dispatcher in a core, {processorPath}/{core}/task/{dispatcherCode}: a virtual thread per group
     * dir, and in it the Task dirs one by one. Only dirs with numeric names are taken - the names are the two parts of
     * a taskId, see DirUtils.getPoweredPath(). Returns when every group is done.
     * Path-based and static on purpose - it's testable without a Spring context.
     *
     * @param cleaner applies the cleaning which is due for a Task, by its id, and tells what was done
     */
    public static ScanResult scanDispatcherDir(Path dispatcherDir, LongFunction<Enums.TaskCleaningAction> cleaner) {
        if (Files.notExists(dispatcherDir)) {
            return ScanResult.EMPTY;
        }
        final List<Future<ScanResult>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Path groupDir : numericDirs(dispatcherDir)) {
                futures.add(executor.submit(() -> scanGroupDir(groupDir, cleaner)));
            }
        }
        // close() of the executor waited for every group
        return futures.stream().map(ProcessorTaskDirCleaner::resultOf).reduce(ScanResult.EMPTY, ScanResult::plus);
    }

    /**
     * The Task dirs of one group dir, one by one. An error with one Task doesn't stop the rest of the group.
     */
    public static ScanResult scanGroupDir(Path groupDir, LongFunction<Enums.TaskCleaningAction> cleaner) {
        final long group = Long.parseLong(groupDir.getFileName().toString());
        ScanResult result = ScanResult.EMPTY;
        for (Path taskDir : numericDirs(groupDir)) {
            final long taskId = group * DigitUtils.DIV + Long.parseLong(taskDir.getFileName().toString());
            Enums.TaskCleaningAction action;
            try {
                action = cleaner.apply(taskId);
            }
            catch (Throwable th) {
                log.error("01.337.080 Error while cleaning dir of task #" + taskId, th);
                action = Enums.TaskCleaningAction.NONE;
            }
            result = result.plus(ScanResult.of(action));
        }
        return result;
    }

    /** @return the sub-dirs of dir whose names are numbers, in the order of those numbers */
    public static List<Path> numericDirs(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isDirectory)
                    .filter(p -> isNumber(p.getFileName().toString()))
                    .sorted(Comparator.comparingLong(p -> Long.parseLong(p.getFileName().toString())))
                    .toList();
        }
        catch (IOException e) {
            log.warn("01.337.100 dir {} can't be listed: {}", dir, e.toString());
            return List.of();
        }
    }

    private static boolean isNumber(String s) {
        // 18 digits at most - always a long
        return !s.isEmpty() && s.length() <= 18 && s.chars().allMatch(Character::isDigit);
    }

    private static ScanResult resultOf(Future<ScanResult> future) {
        try {
            return future.get();
        }
        catch (Throwable th) {
            log.error("01.337.120 Error while cleaning a group of Task dirs", th);
            return ScanResult.EMPTY;
        }
    }
}

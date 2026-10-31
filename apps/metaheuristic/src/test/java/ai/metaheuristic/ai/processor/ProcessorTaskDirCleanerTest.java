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
import ai.metaheuristic.ai.yaml.processor_task.ProcessorCoreTask;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorTaskUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.commons.utils.DirUtils;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.LongFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The scan of Task dirs by cleaning policies, on real dirs and real task.yaml files. The cleaner is composed of the
 * same static functions which ProcessorTaskService.applyCleaning() runs under the lock of a Task - only the lock and
 * the in-memory maps of the service are absent here.
 */
@Execution(ExecutionMode.CONCURRENT)
public class ProcessorTaskDirCleanerTest {

    private static final long DAY = Duration.ofDays(1).toMillis();
    private static final long NOW = 1_800_000_000_000L;

    private static LongFunction<Enums.TaskCleaningAction> cleaner(Path dispatcherDir, long now) {
        return taskId -> {
            final Path taskDir = DirUtils.getPoweredPath(dispatcherDir, taskId);
            final Enums.TaskCleaningAction action = ProcessorTaskService.decideByTaskYaml(taskDir, t -> ArtifactCleanerAtProcessor.cleaningAction(t, now));
            return ProcessorTaskService.applyToTaskDir(taskDir, action);
        };
    }

    private static Path writeTask(Path dispatcherDir, String group, String task, boolean done, boolean clean,
                                  @Nullable String cleaningPolicy, @Nullable Long finishedOn, boolean withAsset) throws IOException {
        final Path taskDir = dispatcherDir.resolve(group).resolve(task);
        Files.createDirectories(taskDir);
        final ProcessorCoreTask t = new ProcessorCoreTask();
        t.taskId = Long.parseLong(group) * 10_000 + Long.parseLong(task);
        t.execContextId = 1L;
        t.dispatcherUrl = "http://localhost:8080";
        t.clean = clean;
        t.cleaningPolicy = cleaningPolicy;
        t.finishedOn = finishedOn;
        t.reported = done;
        t.delivered = done;
        t.completed = done;
        Files.writeString(taskDir.resolve(Consts.TASK_YAML), ProcessorTaskUtils.toString(t));
        if (withAsset) {
            final Path assetDir = taskDir.resolve(ConstsApi.ASSET_DIR);
            Files.createDirectories(assetDir);
            Files.writeString(assetDir.resolve("payload.py"), "print(1)");
        }
        return taskDir;
    }

    @Test
    public void test_scan_appliesDuePoliciesOnly(@TempDir Path dispatcherDir) throws IOException {
        final String week = "ASSETS, FULL_AFTER_ONE_WEEK";
        final Path expired = writeTask(dispatcherDir, "41", "456", true, false, week, NOW - 8 * DAY, true);
        final Path recent = writeTask(dispatcherDir, "41", "457", true, false, week, NOW - DAY, true);
        final Path notDone = writeTask(dispatcherDir, "41", "458", false, false, week, NOW - 8 * DAY, true);
        final Path noPolicy = writeTask(dispatcherDir, "42", "1", true, false, null, NOW - 8 * DAY, true);
        final Path all = writeTask(dispatcherDir, "42", "2", true, false, "ALL", NOW, false);
        final Path clean = writeTask(dispatcherDir, "42", "3", true, true, null, NOW, false);

        final ProcessorTaskDirCleaner.ScanResult r = ProcessorTaskDirCleaner.scanDispatcherDir(dispatcherDir, cleaner(dispatcherDir, NOW));

        assertEquals(new ProcessorTaskDirCleaner.ScanResult(6, 3, 1), r);
        assertTrue(Files.notExists(expired), "a week after finishedOn - the whole dir, as ALL");
        assertTrue(Files.exists(recent.resolve(Consts.TASK_YAML)), "a day after finishedOn - the dir is kept");
        assertTrue(Files.notExists(recent.resolve(ConstsApi.ASSET_DIR)), "but ASSETS of the list is due at once");
        assertTrue(Files.exists(notDone.resolve(ConstsApi.ASSET_DIR)), "a Task which isn't done is never touched");
        assertTrue(Files.exists(noPolicy.resolve(ConstsApi.ASSET_DIR)), "no policy, no 'clean' - kept for analysis");
        assertTrue(Files.notExists(all));
        assertTrue(Files.notExists(clean), "SourceCode's 'clean' is applied to a not loaded Task as well");
        assertTrue(Files.exists(dispatcherDir.resolve("41")), "group dirs themselves are kept");
    }

    @Test
    public void test_scan_secondRun_findsNothingMore(@TempDir Path dispatcherDir) throws IOException {
        writeTask(dispatcherDir, "41", "456", true, false, "ASSETS, FULL_AFTER_ONE_WEEK", NOW - 8 * DAY, true);
        writeTask(dispatcherDir, "41", "457", true, false, "ASSETS, FULL_AFTER_ONE_WEEK", NOW - DAY, true);

        ProcessorTaskDirCleaner.scanDispatcherDir(dispatcherDir, cleaner(dispatcherDir, NOW));
        final ProcessorTaskDirCleaner.ScanResult second = ProcessorTaskDirCleaner.scanDispatcherDir(dispatcherDir, cleaner(dispatcherDir, NOW));

        assertEquals(new ProcessorTaskDirCleaner.ScanResult(1, 0, 0), second, "the asset dir is already gone, the week hasn't passed");
    }

    @Test
    public void test_scan_theSameTaskOnceItsWeekHasPassed(@TempDir Path dispatcherDir) throws IOException {
        final Path recent = writeTask(dispatcherDir, "41", "457", true, false, "ASSETS, FULL_AFTER_ONE_WEEK", NOW - DAY, false);

        ProcessorTaskDirCleaner.scanDispatcherDir(dispatcherDir, cleaner(dispatcherDir, NOW + 6 * DAY - 1));
        assertTrue(Files.exists(recent));
        ProcessorTaskDirCleaner.scanDispatcherDir(dispatcherDir, cleaner(dispatcherDir, NOW + 6 * DAY));
        assertTrue(Files.notExists(recent));
    }

    @Test
    public void test_scan_dirsWhichAreNotTaskDirs_areLeftAndNotCounted(@TempDir Path dispatcherDir) throws IOException {
        Files.createDirectories(dispatcherDir.resolve("tmp").resolve("1"));
        Files.createDirectories(dispatcherDir.resolve("41").resolve("x"));
        Files.writeString(dispatcherDir.resolve("41").resolve("readme.txt"), "x");
        final Path withoutYaml = Files.createDirectories(dispatcherDir.resolve("41").resolve("10"));
        final Path broken = Files.createDirectories(dispatcherDir.resolve("41").resolve("11"));
        Files.writeString(broken.resolve(Consts.TASK_YAML), "taskId: [ not a task");

        final ProcessorTaskDirCleaner.ScanResult r = ProcessorTaskDirCleaner.scanDispatcherDir(dispatcherDir, cleaner(dispatcherDir, NOW));

        assertEquals(new ProcessorTaskDirCleaner.ScanResult(2, 0, 0), r, "41/10 and 41/11 are Task dirs by name, nothing else is");
        assertTrue(Files.exists(dispatcherDir.resolve("tmp").resolve("1")));
        assertTrue(Files.exists(dispatcherDir.resolve("41").resolve("x")));
        assertTrue(Files.exists(withoutYaml), "a dir without task.yaml is the startup scan's business");
        assertTrue(Files.exists(broken.resolve(Consts.TASK_YAML)), "an unreadable task.yaml is never a reason to delete");
    }

    @Test
    public void test_scan_absentDispatcherDir(@TempDir Path dir) {
        assertEquals(ProcessorTaskDirCleaner.ScanResult.EMPTY,
                ProcessorTaskDirCleaner.scanDispatcherDir(dir.resolve("absent"), cleaner(dir, NOW)));
    }

    @Test
    public void test_scan_errorWithOneTask_doesntStopTheGroup(@TempDir Path dispatcherDir) throws IOException {
        writeTask(dispatcherDir, "41", "456", true, false, "ALL", NOW, false);
        final Path second = writeTask(dispatcherDir, "41", "457", true, false, "ALL", NOW, false);
        final LongFunction<Enums.TaskCleaningAction> real = cleaner(dispatcherDir, NOW);

        final ProcessorTaskDirCleaner.ScanResult r = ProcessorTaskDirCleaner.scanDispatcherDir(dispatcherDir, taskId -> {
            if (taskId==410456L) {
                throw new IllegalStateException("a failure of one Task");
            }
            return real.apply(taskId);
        });

        assertEquals(new ProcessorTaskDirCleaner.ScanResult(2, 1, 0), r);
        assertTrue(Files.notExists(second));
    }

    @Test
    public void test_numericDirs_orderedByNumber(@TempDir Path dir) throws IOException {
        for (String name : List.of("10", "9", "100", "abc", "1a")) {
            Files.createDirectories(dir.resolve(name));
        }
        Files.writeString(dir.resolve("5"), "a file, not a dir");

        assertEquals(List.of(dir.resolve("9"), dir.resolve("10"), dir.resolve("100")), ProcessorTaskDirCleaner.numericDirs(dir));
    }

    @Test
    public void test_taskYaml_cleaningPolicyList_roundTrip(@TempDir Path dispatcherDir) throws IOException {
        final Path taskDir = writeTask(dispatcherDir, "41", "456", true, false, "ASSETS, FULL_AFTER_ONE_WEEK", NOW, false);
        final String yaml = Files.readString(taskDir.resolve(Consts.TASK_YAML));
        assertTrue(yaml.contains("cleaningPolicy: ASSETS, FULL_AFTER_ONE_WEEK"), yaml);

        try (InputStream is = Files.newInputStream(taskDir.resolve(Consts.TASK_YAML))) {
            assertEquals("ASSETS, FULL_AFTER_ONE_WEEK", ProcessorTaskUtils.to(is).cleaningPolicy);
        }
    }

    @Test
    public void test_taskYaml_writtenWhenItWasAnEnum_isReadAsOneName(@TempDir Path dispatcherDir) throws IOException {
        final Path taskDir = writeTask(dispatcherDir, "41", "456", true, false, "ASSETS, FULL_AFTER_ONE_WEEK", NOW, true);
        final Path taskYaml = taskDir.resolve(Consts.TASK_YAML);
        Files.writeString(taskYaml, Files.readString(taskYaml).replace("cleaningPolicy: ASSETS, FULL_AFTER_ONE_WEEK", "cleaningPolicy: ASSETS"));

        final ProcessorTaskDirCleaner.ScanResult r = ProcessorTaskDirCleaner.scanDispatcherDir(dispatcherDir, cleaner(dispatcherDir, NOW + 30 * DAY));

        assertEquals(new ProcessorTaskDirCleaner.ScanResult(1, 0, 1), r, "ASSETS only - no week, the dir stays");
        assertTrue(Files.exists(taskYaml));
    }

    @Test
    public void test_scanResult_of() {
        assertEquals(new ProcessorTaskDirCleaner.ScanResult(1, 0, 0), ProcessorTaskDirCleaner.ScanResult.of(Enums.TaskCleaningAction.NONE));
        assertEquals(new ProcessorTaskDirCleaner.ScanResult(1, 1, 0), ProcessorTaskDirCleaner.ScanResult.of(Enums.TaskCleaningAction.TASK_DIR));
        assertEquals(new ProcessorTaskDirCleaner.ScanResult(1, 0, 1), ProcessorTaskDirCleaner.ScanResult.of(Enums.TaskCleaningAction.ASSET_DIR));
    }
}

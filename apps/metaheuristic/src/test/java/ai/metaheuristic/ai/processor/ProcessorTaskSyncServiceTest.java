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
import ai.metaheuristic.ai.processor.data.ProcessorData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Every @Test uses keys under its own @TempDir - the locks are static and @Test methods run concurrently.
 */
@Execution(CONCURRENT)
class ProcessorTaskSyncServiceTest {

    @Test
    public void test_key_isAbsoluteAndNormalized(@TempDir Path dir) {
        final String key = ProcessorTaskSyncService.key(dir.resolve("a").resolve("..").resolve("b").resolve(Consts.TASK_YAML));

        assertEquals(dir.resolve("b").resolve(Consts.TASK_YAML).toAbsolutePath().toString(), key);
        assertEquals(key, ProcessorTaskSyncService.key(dir.resolve("b").resolve(Consts.TASK_YAML)), "two spellings of one path are one key");
        assertTrue(Path.of(ProcessorTaskSyncService.key(Path.of("relative", Consts.TASK_YAML))).isAbsolute());
    }

    @Test
    public void test_taskYamlKey_isTheKeyOfTheTaskDirOnDisk(@TempDir Path processorPath) {
        final ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core = new ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef(
            new ProcessorAndCoreData.DispatcherUrl("http://localhost:8080"), "localhost-8080", 1L, "core-1", 2L);

        final ProcessorTaskService service = new ProcessorTaskService(null, null, null, null);
        service.processorPath = processorPath;
        final Path taskYamlOnDisk = service.prepareTaskDir(core, 123456L).resolve(Consts.TASK_YAML);

        // the startup scan locks by the path it found, every other method by taskYamlKey() - one task, one lock
        assertEquals(ProcessorTaskSyncService.key(taskYamlOnDisk), ProcessorTaskService.taskYamlKey(processorPath, core, 123456L));
        assertNotEquals(ProcessorTaskService.taskYamlKey(processorPath, core, 123456L), ProcessorTaskService.taskYamlKey(processorPath, core, 123457L));
    }

    @Test
    public void test_oneKey_oneLock(@TempDir Path dir) {
        final String key = ProcessorTaskSyncService.key(dir.resolve(Consts.TASK_YAML));
        assertSame(ProcessorTaskSyncService.getWriteLock(key), ProcessorTaskSyncService.getWriteLock(key));
    }

    @Test
    public void test_heldLock_blocksTheSameTaskOnly(@TempDir Path dir) throws Exception {
        final String locked = ProcessorTaskSyncService.key(dir.resolve("1").resolve(Consts.TASK_YAML));
        final String other = ProcessorTaskSyncService.key(dir.resolve("2").resolve(Consts.TASK_YAML));
        final CountDownLatch isHeld = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> ProcessorTaskSyncService.getWithSyncVoid(locked, () -> {
                isHeld.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }), executor);
            try {
                isHeld.await();
                assertFalse(tryWriteLock(locked), "the task is locked by another thread");
                assertTrue(tryWriteLock(other), "another task isn't blocked by it");
                assertFalse(tryReadLock(locked), "a read of the locked task waits for the write");
            } finally {
                release.countDown();
            }
            holder.join();
        }
        assertTrue(tryWriteLock(locked), "released");
    }

    private static boolean tryWriteLock(String key) {
        final ReentrantReadWriteLock.WriteLock lock = ProcessorTaskSyncService.getWriteLock(key);
        if (!lock.tryLock()) {
            return false;
        }
        lock.unlock();
        return true;
    }

    private static boolean tryReadLock(String key) {
        final ReentrantReadWriteLock.ReadLock lock = ProcessorTaskSyncService.getReadLock(key);
        if (!lock.tryLock()) {
            return false;
        }
        lock.unlock();
        return true;
    }

    @Test
    public void test_nestedWriteLockOfTheSameTask_isRejected(@TempDir Path dir) {
        final String key = ProcessorTaskSyncService.key(dir.resolve(Consts.TASK_YAML));

        final IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> ProcessorTaskSyncService.getWithSyncVoid(key, () -> ProcessorTaskSyncService.getWithSyncVoid(key, () -> {})));

        assertTrue(e.getMessage().startsWith("01.718.040 "), e.getMessage());
        assertTrue(tryWriteLock(key), "the outer lock must be released after the failure");
    }

    @Test
    public void test_checkWriteLockPresent(@TempDir Path dir) {
        final String key = ProcessorTaskSyncService.key(dir.resolve(Consts.TASK_YAML));

        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> ProcessorTaskSyncService.checkWriteLockPresent(key));
        assertTrue(e.getMessage().startsWith("01.718.020 "), e.getMessage());

        assertDoesNotThrow(() -> ProcessorTaskSyncService.getWithSyncVoid(key, () -> ProcessorTaskSyncService.checkWriteLockPresent(key)));
    }

    @Test
    public void test_readUnderOwnWriteLock_isAllowed(@TempDir Path dir) {
        final String key = ProcessorTaskSyncService.key(dir.resolve(Consts.TASK_YAML));

        assertEquals(42, ProcessorTaskSyncService.getWithSync(key, () -> ProcessorTaskSyncService.getWithReadSync(key, () -> 42)));
    }
}

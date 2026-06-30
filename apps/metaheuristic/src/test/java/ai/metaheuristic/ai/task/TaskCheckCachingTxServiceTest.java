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

package ai.metaheuristic.ai.task;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.CacheProcess;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.cache.CacheTxService;
import ai.metaheuristic.ai.dispatcher.data.CacheData;
import ai.metaheuristic.ai.dispatcher.repositories.CacheProcessRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskCheckCachingService;
import ai.metaheuristic.ai.dispatcher.task.TaskCheckCachingTxService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Objects;
import java.util.UUID;

import static ai.metaheuristic.ai.dispatcher.task.TaskCheckCachingService.PrepareDataState.ok;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Capability test: MH must be able to
 *   (1) CACHE the result (the output variable) produced by a task's execution, and
 *   (2) RESTORE that result from the cache on a later CHECK_CACHE pass ({@code copied_from_cache}),
 *       byte-for-byte, without re-executing the task.
 *
 * V3 harness. Determinism is by construction, not by suppression: the ExecContext is produced but
 * deliberately NOT started ({@link #step_0_0_produceTasks_withoutStarting}). The task reaches
 * CHECK_CACHE asynchronously (InitVariables: INIT -> CHECK_CACHE); because a non-STARTED ExecContext
 * is invisible to the async allocator/cache-checker
 * (ExecContextTaskAssigningTopLevelService.findAllStartedIds()), once the task settles in CHECK_CACHE
 * nothing advances it further. So we simply wait for that settled state. There is no reliance on
 * TaskCheckCachingService.disableCacheChecking.
 *
 * @author Sergio Lissner
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class TaskCheckCachingTxServiceTest extends PreparingSourceCode {

    @Autowired private TaskRepository taskRepository;
    @Autowired private VariableTxService variableTxService;
    @Autowired private CacheTxService cacheTxService;
    @Autowired private CacheProcessRepository cacheProcessRepository;
    @Autowired private TaskCheckCachingService taskCheckCachingService;
    @Autowired private TaskCheckCachingTxService taskCheckCachingTxService;

    private final String textWithUUID = UUID.randomUUID().toString();

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/test-caching/for-testing-variable-caching.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @Test
    public void test_cacheResultOfExecution_andRestoreFromCache() throws Exception {
        // produce the pipeline but keep the ExecContext OUT of STARTED.
        step_0_0_produceTasks_withoutStarting();

        TaskImpl task = taskRepository.findByExecContextIdReadOnly(getExecContextForTest().id).stream()
                .filter(t -> !t.getTaskParamsYaml().task.function.code.equals(CommonConsts.MH_FINISH_FUNCTION))
                .findFirst().orElseThrow();
        final Long taskId = Objects.requireNonNull(task.id);

        // The task reaches CHECK_CACHE asynchronously (InitVariables: INIT -> CHECK_CACHE). Because the
        // ExecContext is STOPPED, once the task settles in CHECK_CACHE nothing advances it further, so
        // we wait for that settled state - deterministically, without any kill-switch.
        awaitTaskExecState(taskId, EnumsApi.TaskExecState.CHECK_CACHE);

        task = Objects.requireNonNull(taskRepository.findByIdReadOnly(taskId));
        assertEquals(EnumsApi.TaskExecState.CHECK_CACHE.value, task.execState);

        final TaskParamsYaml tpy = task.getTaskParamsYaml();
        assertEquals(1, tpy.task.outputs.size());
        final Long variableId = tpy.task.outputs.get(0).id;
        assertNotNull(variableId);

        // make sure no stale cache entry exists for this cache key
        final CacheData.SimpleKey key = taskCheckCachingService.getSimpleKey(getExecContextForTest().getExecContextParamsYaml(), task);
        assertNotNull(key);
        CacheProcess cacheProcess = cacheProcessRepository.findByKeySha256LengthReadOnly(key.key());
        if (cacheProcess != null) {
            taskCheckCachingTxService.invalidateCacheItem(cacheProcess.id);
        }
        assertNull(cacheProcessRepository.findByKeySha256LengthReadOnly(key.key()));

        // (a) produce a result: store a known value into the task's output variable
        VariableSyncService.getWithSyncVoidForCreation(variableId,
                () -> variableTxService.storeStringInVariable(getExecContextForTest().id, taskId, tpy.task.outputs.get(0), textWithUUID));

        // (b) CACHE THE RESULT
        final ExecContextParamsYaml.Process p = getExecContextForTest().getExecContextParamsYaml().findProcess(tpy.task.processCode);
        assertNotNull(p);
        cacheTxService.storeVariablesTx(tpy, p.function);
        assertNotNull(cacheProcessRepository.findByKeySha256LengthReadOnly(key.key()), "the result wasn't cached");

        // (c) wipe the result, so the ONLY way to get it back is from the cache
        VariableSyncService.getWithSyncVoidForCreation(variableId,
                () -> variableTxService.resetVariableTx(getExecContextForTest().id, variableId));
        Variable v = variableTxService.getVariable(variableId);
        assertNotNull(v);
        assertFalse(v.inited);
        assertTrue(v.nullified);

        // task is still held in CHECK_CACHE (STOPPED ExecContext -> nothing advanced it)
        task = Objects.requireNonNull(taskRepository.findByIdReadOnly(taskId));
        assertEquals(EnumsApi.TaskExecState.CHECK_CACHE.value, task.execState);

        // (d) the cache must be discoverable for this task
        final TaskCheckCachingService.PrepareData prepareData = taskCheckCachingService.getCacheProcess(getExecContextForTest().asSimple(), taskId);
        assertEquals(ok, prepareData.state);
        assertNotNull(prepareData.cacheProcess);

        // (e) THE CAPABILITY: checkCaching restores the result from the cache
        final TaskCheckCachingTxService.CheckCachingStatus status = TaskSyncService.getWithSync(taskId,
                () -> taskCheckCachingTxService.checkCaching(getExecContextForTest().id, taskId, prepareData.cacheProcess));
        assertEquals(TaskCheckCachingTxService.CheckCachingStatus.copied_from_cache, status, "Actual: " + status);

        // (f) the output variable is restored from cache, byte-for-byte
        v = variableTxService.getVariable(variableId);
        assertNotNull(v);
        assertTrue(v.inited);
        assertFalse(v.nullified);
        assertNotNull(v.variableBlobId);
        assertEquals(textWithUUID, variableTxService.getVariableDataAsString(variableId));
    }

    private void awaitTaskExecState(Long taskId, EnumsApi.TaskExecState expected) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + 20_000L;
        Integer actual = null;
        while (System.currentTimeMillis() < deadline) {
            final TaskImpl t = taskRepository.findByIdReadOnly(taskId);
            actual = (t == null) ? null : t.execState;
            if (actual != null && actual == expected.value) {
                return;
            }
            Thread.sleep(100L);
        }
        fail("task #" + taskId + " didn't reach " + expected + " (value=" + expected.value + "); actual execState=" + actual);
    }

}

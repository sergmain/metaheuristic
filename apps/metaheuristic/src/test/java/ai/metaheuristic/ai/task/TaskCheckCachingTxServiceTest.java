/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.CacheProcess;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.cache.CacheTxService;
import ai.metaheuristic.ai.dispatcher.data.CacheData;
import ai.metaheuristic.ai.dispatcher.repositories.CacheProcessRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskCheckCachingService;
import ai.metaheuristic.ai.dispatcher.task.TaskCheckCachingTxService;
import ai.metaheuristic.ai.dispatcher.task.TaskExecStateService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import static ai.metaheuristic.ai.dispatcher.task.TaskCheckCachingService.PrepareDataState.ok;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sergio Lissner
 * Date: 6/12/2023
 * Time: 5:47 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
//@ActiveProfiles({"dispatcher", "mysql"})
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
public class TaskCheckCachingTxServiceTest extends PreparingSourceCode {

    @Autowired private TaskCheckCachingService taskCheckCachingTopLevelService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private VariableTxService variableTxService;
    @Autowired private CacheTxService cacheTxService;
    @Autowired private CacheProcessRepository cacheProcessRepository;
    @Autowired private TaskCheckCachingTxService taskCheckCachingTxService;
    @Autowired private TaskExecStateService taskExecStateService;

    private final String textWithUUID = UUID.randomUUID().toString();

    @SneakyThrows
    @Override
    public String getSourceCodeYamlAsString() {
        return IOUtils.resourceToString("/source_code/yaml/test-caching/for-testing-variable-caching.yaml", StandardCharsets.UTF_8);
    }

    @Test
    public void test() throws IOException {
        Variable v;

        taskCheckCachingTopLevelService.disableCacheChecking = true;

        step_0_0_produceTasks();

        TaskImpl task = taskRepository.findByExecContextIdReadOnly(getExecContextForTest().id).stream()
                .filter(t->!t.getTaskParamsYaml().task.function.code.equals(Consts.MH_FINISH_FUNCTION))
                .findFirst().orElseThrow();

        final Long taskId = Objects.requireNonNull(task.id);

        assertEquals(EnumsApi.TaskExecState.CHECK_CACHE.value, task.execState);
        final TaskParamsYaml tpy = task.getTaskParamsYaml();
        assertEquals(1, tpy.task.outputs.size());
        final Long variableId = tpy.task.outputs.get(0).id;
        assertNotNull(variableId);


        CacheData.SimpleKey key = taskCheckCachingTopLevelService.getSimpleKey(getExecContextForTest().getExecContextParamsYaml(), task);
        assertNotNull(key);


        CacheProcess cacheProcess = cacheProcessRepository.findByKeySha256LengthReadOnly(key.key());
        if (cacheProcess!=null) {
            taskCheckCachingTxService.invalidateCacheItem(cacheProcess.id);
        }
        cacheProcess = cacheProcessRepository.findByKeySha256LengthReadOnly(key.key());
        assertNull(cacheProcess);


        VariableSyncService.getWithSyncVoidForCreation(variableId,
                ()-> variableTxService.storeStringInVariable(getExecContextForTest().id, taskId, tpy.task.outputs.get(0), textWithUUID));

        ExecContextParamsYaml.Process p = getExecContextForTest().getExecContextParamsYaml().findProcess(tpy.task.processCode);
        assertNotNull(p);
        cacheTxService.storeVariablesTx(tpy, p.function);


        cacheProcess = cacheProcessRepository.findByKeySha256LengthReadOnly(key.key());
        assertNotNull(cacheProcess);

        v = variableTxService.getVariable(variableId);
        VariableSyncService.getWithSyncVoidForCreation(variableId,
                ()-> variableTxService.resetVariableTx(getExecContextForTest().id, variableId));

        v = variableTxService.getVariable(variableId);
        assertNotNull(v);
        assertFalse(v.inited);
        assertTrue(v.nullified);

        TaskSyncService.getWithSyncVoid(taskId,
                ()-> taskExecStateService.updateTaskExecStates(taskId, EnumsApi.TaskExecState.CHECK_CACHE));

        v = variableTxService.getVariable(variableId);

        task = taskRepository.findByIdReadOnly(taskId);
        assertNotNull(task);
        assertEquals(EnumsApi.TaskExecState.CHECK_CACHE.value, task.execState);


        TaskCheckCachingService.PrepareData prepareData = taskCheckCachingTopLevelService.getCacheProcess(getExecContextForTest().asSimple(), taskId);
        assertEquals(ok, prepareData.state);
        assertNotNull(prepareData.cacheProcess);

        v = variableTxService.getVariable(variableId);

        TaskCheckCachingTxService.CheckCachingStatus status = taskCheckCachingTxService.checkCaching(getExecContextForTest().id, taskId, prepareData.cacheProcess);
        assertEquals(TaskCheckCachingTxService.CheckCachingStatus.copied_from_cache, status, "Actual: " + status);

        v = variableTxService.getVariable(variableId);
        assertNotNull(v);
        assertTrue(v.inited);
        assertFalse(v.nullified);
        assertNotNull(v.variableBlobId);

        String s = variableTxService.getVariableDataAsString(variableId);
        assertEquals(textWithUUID, s);

    }

}

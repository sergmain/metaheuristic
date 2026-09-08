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

package ai.metaheuristic.ai.internal_function.meta_storage;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.SharedItEnv;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextStatusService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageData;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageService;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageSyntheticService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepositoryForTest;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.preparing.MhInternalTaskPipelineRunner;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.utils.JsonUtils;
import lombok.SneakyThrows;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which store {@code mh.meta-storage} READS when the process declares {@code synthetic = "true"}.
 *
 * <p>{@code processUpsert} branches on the {@code synthetic} meta and writes to
 * {@code MH_META_STORAGE_SYNTHETIC}; the question here is whether {@code processSelect} reads the
 * table its own process named.
 *
 * <p>❗ The assertion is only meaningful because BOTH stores hold a record of the same type for the
 * same company at the moment the Task runs, and the two are distinguishable by recKey and body.
 * Seeding only one would make an empty result and a correct result look alike, and seeding neither
 * would make every implementation pass. Whichever table is read, the answer names itself.
 *
 * <p>No doubles, per RULE-NO-MOCKITO.md: the whole defect lives in a choice between two real
 * services, so a stubbed store could only assert that the test's own stub was called. The Task is
 * produced from a real {@code .mhsc}, driven through the real internal-function dispatch by
 * {@link MhInternalTaskPipelineRunner}, and the result is read out of the real output variable.
 *
 * <p>⚠️ The ExecContext is produced STOPPED and started only after both stores are seeded - a select
 * that ran before the seeding would read two empty tables and pass for the wrong reason.
 *
 * @author Serge
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class MetaStorageFunctionSyntheticSelectTest extends PreparingSourceCode {

    /** Must match the {@code type} meta in the .mhsc - the process declares the kind, not the test. */
    private static final String TYPE = "test.meta-storage-synthetic";

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private TaskRepositoryForTest taskRepositoryForTestLocal;
    @Autowired private ExecContextStatusService execContextStatusServiceLocal;
    @Autowired private ExecContextCache execContextCacheLocal;
    @Autowired private VariableTxService variableTxService;
    @Autowired private MhInternalTaskPipelineRunner pipelineRunner;
    @Autowired private MetaStorageService metaStorageService;
    @Autowired private MetaStorageSyntheticService metaStorageSyntheticService;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang(
                "/source_code/mhsc/meta-storage-synthetic-select-1.0.mhsc", EnumsApi.SourceCodeLang.mhsc, null);
    }

    @AfterEach
    public void afterMetaStorageFunctionSyntheticSelectTest() {
        if (preparingSourceCodeData!=null && preparingSourceCodeData.execContextForTest!=null) {
            clearBothStores(getExecContextForTest().companyId);
            ExecContextSyncService.getWithSyncNullable(getExecContextForTest().id,
                    () -> txSupportForTestingService.deleteByExecContextId(getExecContextForTest().id));
        }
    }

    @SneakyThrows
    @Test
    public void test_selectReadsTheTableTheSyntheticMetaNames() {

        // PHASE #1: produce the Tasks but keep the ExecContext STOPPED, so nothing can execute the
        // select before both stores are seeded
        step_0_0_produceTasks_withoutStarting();

        final Long companyId = getExecContextForTest().companyId;
        assertNotNull(companyId, "PHASE #1: the ExecContext must carry a companyId");

        // PHASE #2: the shared H2 outlives one @Test, so the type is emptied before it is seeded -
        // a record left by an earlier run would change the count this test asserts on
        clearBothStores(companyId);

        final String realRecKey = SharedItEnv.uniqueCode("real");
        final String syntheticRecKey = SharedItEnv.uniqueCode("synthetic");
        metaStorageService.upsert(companyId,
                List.of(new MetaStorageData.Record(TYPE, realRecKey, "from-real-table")));
        metaStorageSyntheticService.upsert(companyId,
                List.of(new MetaStorageData.Record(TYPE, syntheticRecKey, "from-synthetic-table")));

        assertEquals(List.of(realRecKey), metaStorageService.listKeys(companyId, TYPE),
                "PHASE #2: MH_META_STORAGE holds exactly the one real-table record");
        assertEquals(List.of(syntheticRecKey), metaStorageSyntheticService.listKeys(companyId, TYPE),
                "PHASE #2: MH_META_STORAGE_SYNTHETIC holds exactly the one synthetic-table record");

        // PHASE #3: start it and drive the internal Task through the real dispatch path
        startExecContextForTest();
        pipelineRunner.runPipelineToCompletion(getExecContextForTest().id, 20);

        // PHASE #4: read what the select actually wrote into its output variable
        final TaskImpl task = findTaskByFunctionCode(getExecContextForTest().id, Consts.MH_META_STORAGE_FUNCTION);
        assertNotNull(task, "PHASE #4: the mh.meta-storage Task must exist");
        assertEquals(EnumsApi.TaskExecState.OK.value, task.execState,
                "PHASE #4: the select Task must be OK, state=" + EnumsApi.TaskExecState.from(task.execState));

        final String json = variableTxService.getVariableDataAsString(task.getTaskParamsYaml().task.outputs.get(0).id);
        assertNotNull(json, "PHASE #4: the output variable must have been written");
        final MetaStorageData.Record[] records = JsonUtils.getMapper().readValue(json, MetaStorageData.Record[].class);

        // PHASE #5: the process declared synthetic="true", so the record it gets back must be the one
        // that lives in MH_META_STORAGE_SYNTHETIC. A select that answers out of the other table is
        // wrong even though it answers - the two are addressed by the same natural key and only the
        // meta says which one was meant.
        assertEquals(1, records.length, "PHASE #5: exactly one record, json: " + json);
        assertEquals(syntheticRecKey, records[0].recKey(),
                "PHASE #5: synthetic=\"true\" must select out of MH_META_STORAGE_SYNTHETIC");
        assertEquals("from-synthetic-table", records[0].body(),
                "PHASE #5: the body must be the one stored in the synthetic table");
    }

    private void clearBothStores(Long companyId) {
        for (String recKey : metaStorageService.listKeys(companyId, TYPE)) {
            metaStorageService.deleteByNaturalKey(companyId, TYPE, recKey);
        }
        for (String recKey : metaStorageSyntheticService.listKeys(companyId, TYPE)) {
            metaStorageSyntheticService.deleteByNaturalKey(companyId, TYPE, recKey);
        }
    }

    /**
     * The tail of {@code step_0_0_produce_tasks_and_start()}, without the produce - the Tasks already
     * exist and the stores have been seeded in between, which is the whole point of splitting them.
     */
    private void startExecContextForTest() {
        ExecContextSyncService.getWithSyncNullable(getExecContextForTest().id, () -> {
            txSupportForTestingService.toStarted(getExecContextForTest().id);
            setExecContextForTest(Objects.requireNonNull(
                    execContextCacheLocal.findById(getExecContextForTest().getId(), true)));
            return null;
        });
        execContextStatusServiceLocal.resetStatus();
        setExecContextForTest(Objects.requireNonNull(
                execContextCacheLocal.findById(getExecContextForTest().id, true)));
        assertEquals(EnumsApi.ExecContextState.STARTED.code, getExecContextForTest().getState(),
                "the ExecContext must be STARTED before the pipeline is driven");
    }

    @Nullable
    private TaskImpl findTaskByFunctionCode(Long execContextId, String functionCode) {
        for (TaskImpl task : taskRepositoryForTestLocal.findByExecContextIdAsList(execContextId)) {
            if (functionCode.equals(task.getTaskParamsYaml().task.function.code)) {
                return task;
            }
        }
        return null;
    }
}

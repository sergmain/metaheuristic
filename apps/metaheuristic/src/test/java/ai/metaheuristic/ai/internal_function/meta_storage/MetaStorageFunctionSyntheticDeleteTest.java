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
import ai.metaheuristic.ai.preparing.MhInternalTaskPipelineRunner;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.EnumsApi;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What action {@code delete} of {@code mh.meta-storage} removes when the process declares
 * {@code synthetic = "true"} and names one key.
 *
 * <p>The assertion is only meaningful because of how the stores are seeded at the moment the Task runs:
 * the synthetic store holds the named record AND a survivor of the same type, and the production store
 * holds a record under the SAME natural key as the named one. So each wrong implementation fails a phase
 * of its own - an action that does not exist fails the Task (PHASE #4), a delete that ignores its keys
 * takes the survivor with it (PHASE #5), and a delete that answers out of the other table removes the
 * production namesake instead (PHASE #5 and #6).
 *
 * <p>No doubles, per RULE-NO-MOCKITO.md: the Task is produced from a real {@code .mhsc}, driven through
 * the real internal-function dispatch by {@link MhInternalTaskPipelineRunner}, and the result is read out
 * of the two real tables.
 *
 * <p>The ExecContext is produced STOPPED and started only after both stores are seeded - a delete that
 * ran before the seeding would find nothing to delete, and the seeding would then put back what the test
 * expects to be gone.
 *
 * <p>The type and the named key are constants because the process declares them inline, not the test -
 * the same arrangement as {@code MetaStorageFunctionSyntheticSelectTest}. The type is exclusive to this
 * class and emptied in both stores before seeding and after the test, so the fixed key cannot collide.
 *
 * @author Serge
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class MetaStorageFunctionSyntheticDeleteTest extends PreparingSourceCode {

    /** Must match the inline {@code typeName} in the .mhsc - the process declares the kind, not the test. */
    private static final String TYPE = "test.meta-storage-synthetic-delete";

    /** Must match the inline {@code doomedKey} in the .mhsc - the process names what it deletes. */
    private static final String DOOMED_REC_KEY = "doomed";

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private TaskRepositoryForTest taskRepositoryForTestLocal;
    @Autowired private ExecContextStatusService execContextStatusServiceLocal;
    @Autowired private ExecContextCache execContextCacheLocal;
    @Autowired private MhInternalTaskPipelineRunner pipelineRunner;
    @Autowired private MetaStorageService metaStorageService;
    @Autowired private MetaStorageSyntheticService metaStorageSyntheticService;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang(
                "/source_code/mhsc/meta-storage-synthetic-delete-1.0.mhsc", EnumsApi.SourceCodeLang.mhsc, null);
    }

    @AfterEach
    public void afterMetaStorageFunctionSyntheticDeleteTest() {
        if (preparingSourceCodeData!=null && preparingSourceCodeData.execContextForTest!=null) {
            clearBothStores(getExecContextForTest().companyId);
            ExecContextSyncService.getWithSyncNullable(getExecContextForTest().id,
                    () -> txSupportForTestingService.deleteByExecContextId(getExecContextForTest().id));
        }
    }

    @SneakyThrows
    @Test
    public void test_deleteRemovesOnlyTheNamedRecordFromTheTableTheSyntheticMetaNames() {

        // PHASE #1: produce the Tasks but keep the ExecContext STOPPED, so nothing can execute the
        // delete before both stores are seeded
        step_0_0_produceTasks_withoutStarting();

        final Long companyId = getExecContextForTest().companyId;
        assertNotNull(companyId, "PHASE #1: the ExecContext must carry a companyId");

        // PHASE #2: the shared H2 outlives one @Test, so the type is emptied before it is seeded. The
        // synthetic store gets the named record and a survivor; the production store gets a namesake -
        // a record under the same natural key as the one the process names
        clearBothStores(companyId);

        final String survivorRecKey = SharedItEnv.uniqueCode("survivor");
        metaStorageSyntheticService.upsert(companyId, List.of(
                new MetaStorageData.Record(TYPE, DOOMED_REC_KEY, "synthetic-doomed"),
                new MetaStorageData.Record(TYPE, survivorRecKey, "synthetic-survivor")));
        metaStorageService.upsert(companyId,
                List.of(new MetaStorageData.Record(TYPE, DOOMED_REC_KEY, "production-namesake")));

        assertEquals(Set.of(DOOMED_REC_KEY, survivorRecKey),
                Set.copyOf(metaStorageSyntheticService.listKeys(companyId, TYPE)),
                "PHASE #2: MH_META_STORAGE_SYNTHETIC holds the named record and the survivor");
        assertEquals(List.of(DOOMED_REC_KEY), metaStorageService.listKeys(companyId, TYPE),
                "PHASE #2: MH_META_STORAGE holds exactly the namesake of the named record");

        // PHASE #3: start it and drive the internal Tasks through the real dispatch path
        startExecContextForTest();
        pipelineRunner.runPipelineToCompletion(getExecContextForTest().id, 20);

        // PHASE #4: the delete Task must have completed - an action mh.meta-storage does not know fails
        // the Task with 01.942.060
        final TaskImpl task = findTaskByFunctionCode(getExecContextForTest().id, Consts.MH_META_STORAGE_FUNCTION);
        assertNotNull(task, "PHASE #4: the mh.meta-storage Task must exist");
        assertEquals(EnumsApi.TaskExecState.OK.value, task.execState,
                "PHASE #4: the delete Task must be OK, state=" + EnumsApi.TaskExecState.from(task.execState));

        // PHASE #5: exactly the named record left the synthetic store, and the survivor is intact
        assertEquals(List.of(survivorRecKey), metaStorageSyntheticService.listKeys(companyId, TYPE),
                "PHASE #5: only the record the keys named may leave MH_META_STORAGE_SYNTHETIC");
        final List<MetaStorageData.Record> survivors =
                metaStorageSyntheticService.select(companyId, TYPE, List.of(survivorRecKey));
        assertEquals(1, survivors.size(), "PHASE #5: the survivor must still be selectable");
        assertEquals("synthetic-survivor", survivors.getFirst().body(),
                "PHASE #5: the survivor's body must be untouched");

        // PHASE #6: the production store was not touched, although it holds the same natural key. The
        // process declared synthetic="true"; a delete that went to the other table would have removed a
        // production record nobody named
        assertEquals(List.of(DOOMED_REC_KEY), metaStorageService.listKeys(companyId, TYPE),
                "PHASE #6: synthetic=\"true\" must leave MH_META_STORAGE alone");
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

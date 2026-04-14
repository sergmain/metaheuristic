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

package ai.metaheuristic.ai.variable;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextVariableState;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateService;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextVariableStateRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.spi.MhSpi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests ExecContextVariableStateService.removeTaskStates() — verifies that orphan task entries
 * in ExecContextVariableState are properly removed, fixing the variable lookup issue from
 * task-flow-009-cant-find-variable-in-the-same-taskContextId.
 *
 * Production bug scenario:
 * 1. Task#100 (mhdg-rg.decompose-1) at ctx "1,2,5,6#0" creates output "topLevelReqs1"
 *    → registered in ExecContextVariableState
 * 2. Objective reset removes Task#100 from DAG, creates replacement Task#153 at same ctx
 *    → Task#153 creates a NEW "topLevelReqs1" variable
 * 3. ExecContextVariableState still has orphan entry for Task#100
 *    → findVariableInAllInternalContexts finds deleted variable from Task#100 instead of Task#153's
 *    → mh.batch-line-splitter fails: "Variable topLevelReqs1 not found"
 *
 * NOT @Transactional — uses ExecContextVariableStateSyncService.getWithSyncVoid which requires no TX.
 *
 * @author Sergio Lissner
 * Date: 3/15/2026
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureCache
@Slf4j
public class TestRemoveTaskStatesFromVariableState {

    private static final AtomicInteger counter = new AtomicInteger(0);

    @org.junit.jupiter.api.io.TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String dbUrl = "jdbc:h2:file:" + tempDir.resolve("db-h2/mh").toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=0";
        registry.add("spring.datasource.url", () -> dbUrl);
        registry.add("mh.home", () -> tempDir.toAbsolutePath().toString());
        registry.add("spring.profiles.active", () -> "dispatcher,h2,test");
    }

    @BeforeAll
    static void setSystemProperties() {
        MhSpi.cleanUpOnShutdown();
        System.setProperty("mh.home", tempDir.toAbsolutePath().toString());
    }

    @Autowired private VariableTxService variableTxService;
    @Autowired private VariableRepository variableRepository;
    @Autowired private ExecContextRepository execContextRepository;
    @Autowired private ExecContextVariableStateRepository execContextVariableStateRepository;
    @Autowired private ExecContextVariableStateService execContextVariableStateService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private TxSupportForTestingService txSupportForTestingService;

    private String currentVarName;
    private Long execContextId;

    @BeforeEach
    public void before() {
        currentVarName = "test-var-remove-" + counter.incrementAndGet();
        execContextId = txSupportForTestingService.createExecContextForVariableStateTest();
    }

    @AfterEach
    public void after() {
        if (execContextId != null) {
            txSupportForTestingService.deleteExecContextForVariableStateTest(execContextId);
        }
    }

    /**
     * Verifies that removeTaskStates removes the correct entries from ExecContextVariableState
     * and that variable lookup finds the correct (new) variable after cleanup.
     */
    @Test
    public void test_removeTaskStates_fixesOrphanVariableLookup() {
        ExecContextImpl ec = execContextCache.findById(execContextId);
        assertNotNull(ec);
        assertNotNull(ec.execContextVariableStateId);

        // 1. Create OLD variable (from first-run Task#100) at ctx "1,2,5,6#0"
        Variable oldVar = txSupportForTestingService.createInitializedVariable(
                currentVarName, execContextId, "1,2,5,6#0");
        assertNotNull(oldVar);

        // 2. Register OLD task in ExecContextVariableState
        ExecContextVariableStateSyncService.getWithSyncVoid(ec.execContextVariableStateId, () ->
                execContextVariableStateService.registerCreatedTasks(ec.execContextVariableStateId,
                        List.of(makeVariableState(100L, "1,2,5,6#0", oldVar.id, currentVarName))));

        // 3. Verify search finds OLD variable
        Variable found = txSupportForTestingService.findVariableInAllInternalContexts(
                currentVarName, "1,2,5,6#0", execContextId);
        assertNotNull(found, "Before reset: should find variable via ExecContextVariableState");
        assertEquals(oldVar.id, found.id);

        // 4. Simulate reset: delete old variable from DB, create NEW variable at same context
        txSupportForTestingService.deleteVariable(oldVar.id);
        Variable newVar = txSupportForTestingService.createInitializedVariable(
                currentVarName, execContextId, "1,2,5,6#0");
        assertNotNull(newVar);
        assertNotEquals(oldVar.id, newVar.id);

        // 5. Register NEW task (Task#153) in ExecContextVariableState
        ExecContextVariableStateSyncService.getWithSyncVoid(ec.execContextVariableStateId, () ->
                execContextVariableStateService.registerCreatedTasks(ec.execContextVariableStateId,
                        List.of(makeVariableState(153L, "1,2,5,6#0", newVar.id, currentVarName))));

        // 6. BUG: search finds orphan entry for Task#100 → variable deleted → returns null
        found = txSupportForTestingService.findVariableInAllInternalContexts(
                currentVarName, "1,2,5,6#0", execContextId);
        assertNull(found, "BUG: orphan entry in VariableState points to deleted variable, search returns null");

        // 7. FIX: remove orphan Task#100 entry from ExecContextVariableState
        ExecContextVariableStateSyncService.getWithSyncVoid(ec.execContextVariableStateId, () ->
                execContextVariableStateService.removeTaskStates(ec.execContextVariableStateId, Set.of(100L)));

        // 8. After fix: search finds NEW variable from Task#153
        found = txSupportForTestingService.findVariableInAllInternalContexts(
                currentVarName, "1,2,5,6#0", execContextId);
        assertNotNull(found, "After cleanup: should find the NEW variable from Task#153");
        assertEquals(newVar.id, found.id, "After cleanup: found variable should be the new one");

        // 9. Verify state has only Task#153's entry
        ExecContextVariableState ecvs = execContextVariableStateRepository.findById(ec.execContextVariableStateId).orElseThrow();
        ExecContextApiData.ExecContextVariableStates info = ecvs.getExecContextVariableStateInfo();
        long matchingEntries = info.states.stream().filter(s -> s.taskId == 100L).count();
        assertEquals(0, matchingEntries, "Task#100 entry should be removed from VariableState");
        long newEntries = info.states.stream().filter(s -> s.taskId == 153L).count();
        assertEquals(1, newEntries, "Task#153 entry should remain in VariableState");
    }

    private ExecContextApiData.VariableState makeVariableState(Long taskId, String taskContextId, Long variableId, String variableName) {
        ExecContextApiData.VariableInfo vi = new ExecContextApiData.VariableInfo(variableId, variableName, EnumsApi.VariableContext.local, ".txt");
        return new ExecContextApiData.VariableState(
                taskId, 0L, 0L, taskContextId, "test-process", "test-function",
                null, List.of(vi));
    }
}

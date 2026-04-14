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

package ai.metaheuristic.ai.variable;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextVariableState;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextVariableStateRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.spi.MhSpi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ch.qos.logback.classic.LoggerContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for VariableTxService#findVariableInAllInternalContexts(String, String, Long)
 *
 * Bug scenario: a variable created at taskContextId="1,2#1" cannot be found
 * when searching from taskContextId="1,2,5" because the parent-context walk
 * goes "1,2,5" -> "1,2" -> "1" -> null and never visits "1,2#1".
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
@Slf4j
@Transactional
public class TestFindVariableInAllInternalContexts {

    private static final AtomicInteger counter = new AtomicInteger(0);

    @org.junit.jupiter.api.io.TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String dbUrl = "jdbc:h2:file:" + tempDir.resolve("db-h2/mh").toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE";
        registry.add("spring.datasource.url", () -> dbUrl);
        registry.add("mh.home", () -> tempDir.toAbsolutePath().toString());
        registry.add("spring.profiles.active", () -> "dispatcher,h2,test");
    }

    @BeforeAll
    static void setSystemProperties() {
        MhSpi.cleanUpOnShutdown();
        System.setProperty("mh.home", tempDir.toAbsolutePath().toString());
    }

    @AfterAll
    static void cleanupLogging() {
        MhSpi.cleanUpOnShutdown();
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.stop();
    }

    @Autowired private VariableTxService variableTxService;
    @Autowired private VariableRepository variableRepository;
    @Autowired private ExecContextRepository execContextRepository;
    @Autowired private ExecContextVariableStateRepository execContextVariableStateRepository;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private TxSupportForTestingService txSupportForTestingService;

    private String currentVarName;

    @BeforeEach
    public void before() {
        currentVarName = "test-var-find-" + counter.incrementAndGet();
    }

    /**
     * Creates a Variable in DB and returns it.
     */
    private Variable createVariable(String taskContextId, Long execContextId) {
        byte[] data = "ACTIVE".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream is = new ByteArrayInputStream(data);
        return variableTxService.createInitializedTx(is, data.length, currentVarName, null, execContextId, taskContextId, EnumsApi.VariableType.text);
    }

    /**
     * Creates an ExecContextVariableState with VariableState entries, then creates
     * an ExecContext pointing to it. Returns the execContextId.
     */
    private Long setupExecContext(List<ExecContextApiData.VariableState> variableStates) {
        // Create and save ExecContextVariableState
        ExecContextVariableState ecvs = new ExecContextVariableState();
        ecvs.createdOn = System.currentTimeMillis();
        ExecContextApiData.ExecContextVariableStates info = new ExecContextApiData.ExecContextVariableStates();
        info.states.addAll(variableStates);
        ecvs.updateParams(info);
        ecvs = execContextVariableStateRepository.save(ecvs);

        // Create and save ExecContext
        ExecContextImpl ec = new ExecContextImpl();
        ec.sourceCodeId = 1L;
        ec.companyId = 1L;
        ec.accountId = 1L;
        ec.createdOn = System.currentTimeMillis();
        ec.state = EnumsApi.ExecContextState.STARTED.code;
        ec.execContextVariableStateId = ecvs.id;
        ec.execContextGraphId = 0L;
        ec.execContextTaskStateId = 0L;
        ec.setParams("version: 1\nprocesses: []\nvariables:\n  inline: {}\n  inputs: []\n  outputs: []\n");
        ec = execContextCache.save(ec);

        // Update the ecvs to link back
        ecvs.execContextId = ec.id;
        execContextVariableStateRepository.save(ecvs);

        return ec.id;
    }

    /**
     * Helper to create a VariableState entry with the variable as an output.
     */
    private ExecContextApiData.VariableState makeVariableState(String taskContextId, Long variableId, String variableName) {
        ExecContextApiData.VariableInfo vi = new ExecContextApiData.VariableInfo(variableId, variableName, EnumsApi.VariableContext.local, ".txt");
        return new ExecContextApiData.VariableState(
                1L, 0L, 0L, taskContextId, "test-process", "test-function",
                null, List.of(vi));
    }

    /**
     * Baseline: variable at "1,2" should be found from "1,2,5"
     * Walk: "1,2,5" -> "1,2" (found)
     */
    @Test
    public void test_findVariable_atParentContext_simple() {
        Long execContextId = setupExecContext(List.of());
        Variable created = createVariable("1,2", execContextId);

        // Update ExecContextVariableState with the variable info
        ExecContextImpl ec = execContextCache.findById(execContextId);
        ExecContextVariableState ecvs = execContextVariableStateRepository.findById(ec.execContextVariableStateId).orElseThrow();
        ExecContextApiData.ExecContextVariableStates info = ecvs.getExecContextVariableStateInfo();
        info.states.add(makeVariableState("1,2", created.id, currentVarName));
        ecvs.updateParams(info);
        execContextVariableStateRepository.save(ecvs);

        Variable found = variableTxService.findVariableInAllInternalContexts(currentVarName, "1,2,5", execContextId);
        assertNotNull(found, "Variable at '1,2' should be found when searching from '1,2,5'");
        assertEquals(created.id, found.id);
    }

    /**
     * Baseline: variable at "1" should be found from "1,2,5"
     * Walk: "1,2,5" -> "1,2" -> "1" (found)
     */
    @Test
    public void test_findVariable_atGrandparentContext() {
        Long execContextId = setupExecContext(List.of());
        Variable created = createVariable("1", execContextId);

        ExecContextImpl ec = execContextCache.findById(execContextId);
        ExecContextVariableState ecvs = execContextVariableStateRepository.findById(ec.execContextVariableStateId).orElseThrow();
        ExecContextApiData.ExecContextVariableStates info = ecvs.getExecContextVariableStateInfo();
        info.states.add(makeVariableState("1", created.id, currentVarName));
        ecvs.updateParams(info);
        execContextVariableStateRepository.save(ecvs);

        Variable found = variableTxService.findVariableInAllInternalContexts(currentVarName, "1,2,5", execContextId);
        assertNotNull(found, "Variable at '1' should be found when searching from '1,2,5'");
        assertEquals(created.id, found.id);
    }

    /**
     * Variable at exact same context should be found
     */
    @Test
    public void test_findVariable_atSameContext() {
        Long execContextId = setupExecContext(List.of());
        Variable created = createVariable("1,2,5", execContextId);

        ExecContextImpl ec = execContextCache.findById(execContextId);
        ExecContextVariableState ecvs = execContextVariableStateRepository.findById(ec.execContextVariableStateId).orElseThrow();
        ExecContextApiData.ExecContextVariableStates info = ecvs.getExecContextVariableStateInfo();
        info.states.add(makeVariableState("1,2,5", created.id, currentVarName));
        ecvs.updateParams(info);
        execContextVariableStateRepository.save(ecvs);

        Variable found = variableTxService.findVariableInAllInternalContexts(currentVarName, "1,2,5", execContextId);
        assertNotNull(found, "Variable at '1,2,5' should be found when searching from '1,2,5'");
        assertEquals(created.id, found.id);
    }

    /**
     * BUG REPRODUCTION: variable at "1,2#1" should be reachable from "1,2,5"
     *
     * This is the exact scenario from the production bug:
     * - Variable amendmentStatus is created at taskContextId="1,2#1"
     * - Task at taskContextId="1,2,5" tries to find it as input
     * - The parent-context walk from "1,2,5" goes: "1,2,5" -> "1,2" -> "1" -> null
     * - At "1,2", the method should also find variables at "1,2#1" (same processContextId)
     */
    @Test
    public void test_findVariable_atHashContext_fromSibling_BUG() {
        Long execContextId = setupExecContext(List.of());
        Variable created = createVariable("1,2#1", execContextId);

        ExecContextImpl ec = execContextCache.findById(execContextId);
        ExecContextVariableState ecvs = execContextVariableStateRepository.findById(ec.execContextVariableStateId).orElseThrow();
        ExecContextApiData.ExecContextVariableStates info = ecvs.getExecContextVariableStateInfo();
        info.states.add(makeVariableState("1,2#1", created.id, currentVarName));
        ecvs.updateParams(info);
        execContextVariableStateRepository.save(ecvs);

        Variable found = variableTxService.findVariableInAllInternalContexts(currentVarName, "1,2,5", execContextId);
        assertNotNull(found,
                "Variable at '1,2#1' should be found when searching from '1,2,5'. " +
                "Both share processContextId '1,2'.");
        assertEquals(created.id, found.id);
    }

    /**
     * Verify that finding a variable at a #-suffixed context works when searching
     * from that exact context.
     */
    @Test
    public void test_findVariable_atHashContext_exact() {
        Long execContextId = setupExecContext(List.of());
        Variable created = createVariable("1,2#1", execContextId);

        ExecContextImpl ec = execContextCache.findById(execContextId);
        ExecContextVariableState ecvs = execContextVariableStateRepository.findById(ec.execContextVariableStateId).orElseThrow();
        ExecContextApiData.ExecContextVariableStates info = ecvs.getExecContextVariableStateInfo();
        info.states.add(makeVariableState("1,2#1", created.id, currentVarName));
        ecvs.updateParams(info);
        execContextVariableStateRepository.save(ecvs);

        Variable found = variableTxService.findVariableInAllInternalContexts(currentVarName, "1,2#1", execContextId);
        assertNotNull(found, "Variable at '1,2#1' should be found when searching from '1,2#1'");
        assertEquals(created.id, found.id);
    }

    /**
     * From "1,2#1", the parent walk should go to "1".
     * A variable at "1" should be found.
     */
    @Test
    public void test_findVariable_walkFromHashContext_toRoot() {
        Long execContextId = setupExecContext(List.of());
        Variable created = createVariable("1", execContextId);

        ExecContextImpl ec = execContextCache.findById(execContextId);
        ExecContextVariableState ecvs = execContextVariableStateRepository.findById(ec.execContextVariableStateId).orElseThrow();
        ExecContextApiData.ExecContextVariableStates info = ecvs.getExecContextVariableStateInfo();
        info.states.add(makeVariableState("1", created.id, currentVarName));
        ecvs.updateParams(info);
        execContextVariableStateRepository.save(ecvs);

        Variable found = variableTxService.findVariableInAllInternalContexts(currentVarName, "1,2#1", execContextId);
        assertNotNull(found, "Variable at '1' should be found when searching from '1,2#1'");
        assertEquals(created.id, found.id);
    }

    /**
     * Variable at unrelated context should NOT be found.
     */
    @Test
    public void test_findVariable_atUnrelatedContext_notFound() {
        Long execContextId = setupExecContext(List.of());
        Variable created = createVariable("1,3", execContextId);

        ExecContextImpl ec = execContextCache.findById(execContextId);
        ExecContextVariableState ecvs = execContextVariableStateRepository.findById(ec.execContextVariableStateId).orElseThrow();
        ExecContextApiData.ExecContextVariableStates info = ecvs.getExecContextVariableStateInfo();
        info.states.add(makeVariableState("1,3", created.id, currentVarName));
        ecvs.updateParams(info);
        execContextVariableStateRepository.save(ecvs);

        Variable found = variableTxService.findVariableInAllInternalContexts(currentVarName, "1,2,5", execContextId);
        assertNull(found, "Variable at '1,3' should NOT be found when searching from '1,2,5'");
    }

    /**
     * Top-level input variable (like "projectCode") exists in DB at context "1"
     * but has NO VariableState entry in ExecContextVariableState.
     * The DB fallback must find it.
     */
    @Test
    public void test_findVariable_topLevelInput_notInVariableState() {
        // Setup ExecContext with EMPTY VariableState list — no tasks registered yet
        Long execContextId = setupExecContext(List.of());

        // Create the variable in DB at top-level context "1" — simulates
        // how projectCode is created during execContext initialization
        Variable created = createVariable("1", execContextId);

        // Search from context "1" — should find via DB fallback
        Variable found = variableTxService.findVariableInAllInternalContexts(currentVarName, "1", execContextId);
        assertNotNull(found,
                "Top-level variable at '1' with no VariableState entry should be found via DB fallback");
        assertEquals(created.id, found.id);
    }

    /**
     * Top-level input variable exists in DB at context "1" with no VariableState entry.
     * Searching from a deeper context "1,2#1" should also find it via the walk + DB fallback.
     */
    @Test
    public void test_findVariable_topLevelInput_notInVariableState_fromDeepContext() {
        Long execContextId = setupExecContext(List.of());
        Variable created = createVariable("1", execContextId);

        // Search from "1,2#1" — walk goes "1,2#1" -> "1" -> null
        // At "1", the in-memory lookup finds nothing, but DB fallback should find it
        Variable found = variableTxService.findVariableInAllInternalContexts(currentVarName, "1,2#1", execContextId);
        assertNotNull(found,
                "Top-level variable at '1' should be found from '1,2#1' via DB fallback");
        assertEquals(created.id, found.id);
    }

    /**
     * Variable exists in DB at context "1" with no VariableState entry.
     * Searching from "1,2,5" should find it at the "1" step of the walk via DB fallback.
     * This reproduces the "projectCode not found" production bug.
     */
    @Test
    public void test_findVariable_topLevelInput_notInVariableState_fromSiblingContext() {
        Long execContextId = setupExecContext(List.of());
        Variable created = createVariable("1", execContextId);

        // Search from "1,2,5" — walk: "1,2,5" -> "1,2" -> "1" -> null
        Variable found = variableTxService.findVariableInAllInternalContexts(currentVarName, "1,2,5", execContextId);
        assertNotNull(found,
                "Top-level variable at '1' should be found from '1,2,5' via DB fallback at walk step '1'");
        assertEquals(created.id, found.id);
    }

    /**
     * ExecContextVariableState is async — it may be empty when the lookup happens.
     * Variable at "1,2#1" exists in DB, but ExecContextVariableState has no entries yet.
     * Searching from "1,2,5" must still find it via full DB fallback with LIKE query
     * for #-suffixed siblings.
     */
    @Test
    public void test_findVariable_atHashContext_emptyVariableState_fullDbFallback() {
        // Empty VariableState — simulates async delay
        Long execContextId = setupExecContext(List.of());
        Variable created = createVariable("1,2#1", execContextId);

        Variable found = variableTxService.findVariableInAllInternalContexts(currentVarName, "1,2,5", execContextId);
        assertNotNull(found,
                "Variable at '1,2#1' should be found from '1,2,5' even when ExecContextVariableState is empty, " +
                "via full DB fallback with LIKE query for #-suffixed siblings");
        assertEquals(created.id, found.id);
    }

    /**
     * ExecContextVariableState is async — partially populated.
     * Variable at "1,2#1" exists in DB but is NOT in the VariableState entries.
     * Other unrelated VariableState entries exist. Must still find via DB fallback.
     */
    @Test
    public void test_findVariable_atHashContext_partialVariableState_dbFallback() {
        Long execContextId = setupExecContext(List.of());
        Variable created = createVariable("1,2#1", execContextId);

        // Add an unrelated VariableState entry — simulates partial async update
        ExecContextImpl ec = execContextCache.findById(execContextId);
        ExecContextVariableState ecvs = execContextVariableStateRepository.findById(ec.execContextVariableStateId).orElseThrow();
        ExecContextApiData.ExecContextVariableStates info = ecvs.getExecContextVariableStateInfo();
        ExecContextApiData.VariableInfo unrelatedVi = new ExecContextApiData.VariableInfo(999L, "otherVar", EnumsApi.VariableContext.local, ".txt");
        info.states.add(new ExecContextApiData.VariableState(
                1L, 0L, 0L, "1", "some-process", "some-function",
                null, List.of(unrelatedVi)));
        ecvs.updateParams(info);
        execContextVariableStateRepository.save(ecvs);

        Variable found = variableTxService.findVariableInAllInternalContexts(currentVarName, "1,2,5", execContextId);
        assertNotNull(found,
                "Variable at '1,2#1' should be found from '1,2,5' via DB fallback " +
                "when VariableState doesn't contain the relevant entry");
        assertEquals(created.id, found.id);
    }

    @Test
    public void test_findVariable_() {
        Long execContextId = setupExecContext(List.of());
        Variable created = createVariable("1,2,5,6,7|0#1", execContextId);

        // Add an unrelated VariableState entry — simulates partial async update
        ExecContextImpl ec = execContextCache.findById(execContextId);
        ExecContextVariableState ecvs = execContextVariableStateRepository.findById(ec.execContextVariableStateId).orElseThrow();
        ExecContextApiData.ExecContextVariableStates info = ecvs.getExecContextVariableStateInfo();
        ExecContextApiData.VariableInfo unrelatedVi = new ExecContextApiData.VariableInfo(999L, "otherVar", EnumsApi.VariableContext.local, ".txt");
        info.states.add(new ExecContextApiData.VariableState(
                1L, 0L, 0L, "1", "some-process", "some-function",
                null, List.of(unrelatedVi)));
        ecvs.updateParams(info);
        execContextVariableStateRepository.save(ecvs);

        Variable found = variableTxService.findVariableInAllInternalContexts(currentVarName, "1,2,5,6,7,10", execContextId);
        assertNotNull(found,
                "Variable at '1,2,5,6,7|0#1' should be found from '1,2,5,6,7,10' via DB fallback " +
                "when VariableState doesn't contain the relevant entry");
        assertEquals(created.id, found.id);
    }

    /**
     * CT (Characterization Test) — documents current BUGGY behavior.
     *
     * Bug: two sibling batch instances at "1,2,5,6,7|1|0|0#1" and "1,2,5,6,7|1|0|0#2"
     * both have a variable "requirementId1". When searching from "1,2,5,6,7|1|0|0#2",
     * the lookup should return the variable from #2. But due to the processContextId-only
     * match in findVariableIdInStates, it returns whichever comes first in the states list —
     * which is the variable from #1 (wrong branch).
     *
     * Step 1 (Green): assert the buggy behavior (returns #1's variable from #2's context).
     */
    @Test
    public void test_CT_crossBranchVariableLeak_viaVariableState() {
        Long execContextId = setupExecContext(List.of());

        // Create two variables with the same name in two sibling # instances
        Variable varBranch1 = createVariable("1,2,5,6,7|1|0|0#1", execContextId);
        currentVarName = currentVarName; // same name for both — re-use
        // Need to create second variable with same name at different context
        byte[] data2 = "VALUE-FROM-BRANCH-2".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream is2 = new ByteArrayInputStream(data2);
        Variable varBranch2 = variableTxService.createInitializedTx(
                is2, data2.length, currentVarName, null, execContextId,
                "1,2,5,6,7|1|0|0#2", EnumsApi.VariableType.text);

        // Register BOTH in ExecContextVariableState — #1 first, #2 second
        ExecContextImpl ec = execContextCache.findById(execContextId);
        ExecContextVariableState ecvs = execContextVariableStateRepository.findById(ec.execContextVariableStateId).orElseThrow();
        ExecContextApiData.ExecContextVariableStates info = ecvs.getExecContextVariableStateInfo();
        info.states.add(makeVariableState("1,2,5,6,7|1|0|0#1", varBranch1.id, currentVarName));
        info.states.add(makeVariableState("1,2,5,6,7|1|0|0#2", varBranch2.id, currentVarName));
        ecvs.updateParams(info);
        execContextVariableStateRepository.save(ecvs);

        // Search from #2's context — should get varBranch2, but due to bug gets varBranch1
        Variable found = variableTxService.findVariableInAllInternalContexts(
                currentVarName, "1,2,5,6,7|1|0|0#2", execContextId);

        assertNotNull(found);
        // CORRECT: should return varBranch2 (from #2), not varBranch1 (from #1)
        assertEquals(varBranch2.id, found.id,
                "Lookup from #2 should return #2's own variable, not #1's");
    }

    /**
     * Variable at "1,2,5,6,7|0#1" IS registered in ExecContextVariableState.
     * Searching from "1,2,5,6,7,10" should find it via the in-memory path
     * because processContextId("1,2,5,6,7|0#1") == "1,2,5,6,7" matches
     * processContextId of the walk step "1,2,5,6,7".
     *
     * Reproduces the amendmentStatus2 production bug scenario.
     */
    @Test
    public void test_findVariable_pipeAncestor_viaVariableState() {
        Long execContextId = setupExecContext(List.of());
        Variable created = createVariable("1,2,5,6,7|0#1", execContextId);

        // Register the variable in ExecContextVariableState at the correct taskContextId
        ExecContextImpl ec = execContextCache.findById(execContextId);
        ExecContextVariableState ecvs = execContextVariableStateRepository.findById(ec.execContextVariableStateId).orElseThrow();
        ExecContextApiData.ExecContextVariableStates info = ecvs.getExecContextVariableStateInfo();
        info.states.add(makeVariableState("1,2,5,6,7|0#1", created.id, currentVarName));
        ecvs.updateParams(info);
        execContextVariableStateRepository.save(ecvs);

        Variable found = variableTxService.findVariableInAllInternalContexts(currentVarName, "1,2,5,6,7,10", execContextId);
        assertNotNull(found,
                "Variable at '1,2,5,6,7|0#1' should be found from '1,2,5,6,7,10' via in-memory ExecContextVariableState. " +
                "processContextId '1,2,5,6,7' matches for both.");
        assertEquals(created.id, found.id);
    }

    // =====================================================================================
    // Context propagation and variable scoping — comprehensive test suite
    //
    // Context propagation rules (ContextUtils.getCurrTaskContextIdForSubProcesses):
    //   parent "1"            + subprocess "1,2"     → "1,2"           (no ancestor path)
    //   parent "1,2#1"        + subprocess "1,2,5"   → "1,2,5|1"      (ancestor = instance#)
    //   parent "1,2,5|1#0"   + subprocess "1,2,5,6" → "1,2,5,6|1|0"  (chain ancestors)
    //   parent "1,2,5,6|1|0#2" + subprocess "1,2,5,6,7" → "1,2,5,6,7|1|0|2"
    //
    // Parent walk (ContextUtils.deriveParentTaskContextId):
    //   "1,2,5,6,7|1|0|0#2" → "1,2,5,6|1|0#0" → "1,2,5|1#0" → "1,2#1" → "1" → null
    //
    // Variable scoping: a variable is visible from context Y if it's on Y's parent walk chain.
    // Variables in sibling branches (#0 vs #1 at the same level) are NOT visible to each other.
    // =====================================================================================

    /**
     * Helper: creates a variable and registers it in ExecContextVariableState.
     */
    private Variable createAndRegisterVariable(String taskContextId, Long execContextId) {
        Variable v = createVariable(taskContextId, execContextId);

        ExecContextImpl ec = execContextCache.findById(execContextId);
        ExecContextVariableState ecvs = execContextVariableStateRepository.findById(ec.execContextVariableStateId).orElseThrow();
        ExecContextApiData.ExecContextVariableStates info = ecvs.getExecContextVariableStateInfo();
        info.states.add(makeVariableState(taskContextId, v.id, currentVarName));
        ecvs.updateParams(info);
        execContextVariableStateRepository.save(ecvs);

        return v;
    }

    /**
     * Left sibling NOT visible.
     *
     * Variable at "1,2#0" should NOT be found from a child of "1,2#1".
     * Context "1,2,5|1#0" walks: → "1,2#1" → "1" → null.
     * It never visits "1,2#0".
     */
    @Test
    public void test_leftSibling_notVisible_fromDifferentBranchChild() {
        Long execContextId = setupExecContext(List.of());
        Variable varAtBranch0 = createAndRegisterVariable("1,2#0", execContextId);

        // Search from a context under branch #1
        Variable found = variableTxService.findVariableInAllInternalContexts(
                currentVarName, "1,2,5|1#0", execContextId);

        assertNull(found,
                "Variable at '1,2#0' should NOT be visible from '1,2,5|1#0'. " +
                "The walk goes 1,2,5|1#0 → 1,2#1 → 1 → null, never touching 1,2#0.");
    }

    /**
     * Ancestor chain visibility — deep nesting.
     *
     * Variable at "1,2#1" should be found from deeply nested "1,2,5,6,7|1|0|0#2".
     * Walk: "1,2,5,6,7|1|0|0#2" → "1,2,5,6|1|0#0" → "1,2,5|1#0" → "1,2#1" (found)
     */
    @Test
    public void test_ancestorChain_deepNesting_found() {
        Long execContextId = setupExecContext(List.of());
        Variable created = createAndRegisterVariable("1,2#1", execContextId);

        Variable found = variableTxService.findVariableInAllInternalContexts(
                currentVarName, "1,2,5,6,7|1|0|0#2", execContextId);

        assertNotNull(found,
                "Variable at '1,2#1' should be found from '1,2,5,6,7|1|0|0#2'. " +
                "Walk reaches '1,2#1' at depth 3.");
        assertEquals(created.id, found.id);
    }

    /**
     * Top-level variable visible from deeply nested context.
     *
     * Variable at "1" should be found from "1,2,5,6,7|1|0|0#2".
     * Walk: ... → "1,2#1" → "1" (found)
     */
    @Test
    public void test_topLevel_visibleFromDeepNesting() {
        Long execContextId = setupExecContext(List.of());
        Variable created = createAndRegisterVariable("1", execContextId);

        Variable found = variableTxService.findVariableInAllInternalContexts(
                currentVarName, "1,2,5,6,7|1|0|0#2", execContextId);

        assertNotNull(found,
                "Variable at '1' should be found from '1,2,5,6,7|1|0|0#2'. " +
                "Walk eventually reaches '1'.");
        assertEquals(created.id, found.id);
    }

    /**
     * Cross-branch at intermediate level — different ancestor path.
     *
     * Variable at "1,2,5|1#0" should NOT be found from a context under "1,2,5|1#1".
     * Context "1,2,5,6|1|1#0" walks: → "1,2,5|1#1" → "1,2#1" → "1" → null.
     * It never visits "1,2,5|1#0".
     *
     * This tests that sibling batch instances at intermediate nesting levels
     * are properly isolated.
     */
    @Test
    public void test_crossBranch_intermediateLevel_notVisible() {
        Long execContextId = setupExecContext(List.of());
        Variable varAtInstance0 = createAndRegisterVariable("1,2,5|1#0", execContextId);

        // Search from a context under instance #1 at the same intermediate level
        Variable found = variableTxService.findVariableInAllInternalContexts(
                currentVarName, "1,2,5,6|1|1#0", execContextId);

        assertNull(found,
                "Variable at '1,2,5|1#0' should NOT be visible from '1,2,5,6|1|1#0'. " +
                "Walk goes 1,2,5,6|1|1#0 → 1,2,5|1#1 → 1,2#1 → 1 → null. " +
                "Never visits 1,2,5|1#0 (sibling instance).");
    }

    /**
     * Same processContextId but different ancestor path — completely separate DAG branches.
     *
     * Variable at "1,2,5,6,7|1|0|0#1" should NOT be found from "1,2,5,6,7|2|0|0#1".
     * These share processContextId "1,2,5,6,7" but differ in ancestor paths (1|0|0 vs 2|0|0),
     * meaning they descend from different top-level batch instances (#1 vs #2).
     *
     * Walk from "1,2,5,6,7|2|0|0#1": → "1,2,5,6|2|0#0" → "1,2,5|2#0" → "1,2#2" → "1" → null.
     * Never touches ancestor path "1|0|0".
     */
    @Test
    public void test_sameProcessCtxId_differentAncestorPath_notVisible() {
        Long execContextId = setupExecContext(List.of());
        Variable varFromBranch1 = createAndRegisterVariable("1,2,5,6,7|1|0|0#1", execContextId);

        // Search from a context with same processCtxId but different ancestor chain
        Variable found = variableTxService.findVariableInAllInternalContexts(
                currentVarName, "1,2,5,6,7|2|0|0#1", execContextId);

        assertNull(found,
                "Variable at '1,2,5,6,7|1|0|0#1' should NOT be visible from '1,2,5,6,7|2|0|0#1'. " +
                "Same processContextId '1,2,5,6,7' but different ancestor paths (1|0|0 vs 2|0|0).");
    }

    /**
     * Correct branch's variable found among multiple sibling branches.
     *
     * Three batch instances at first level: #0, #1, #2.
     * Each has a variable with the same name at contexts "1,2#0", "1,2#1", "1,2#2".
     * Search from a deep child of #1 should find only #1's variable.
     *
     * Walk from "1,2,5|1#0": → "1,2#1" (found — exact match for #1's variable)
     */
    @Test
    public void test_correctBranch_amongThreeSiblings() {
        Long execContextId = setupExecContext(List.of());

        Variable var0 = createAndRegisterVariable("1,2#0", execContextId);

        currentVarName = currentVarName; // reuse same name
        byte[] data1 = "branch-1".getBytes(StandardCharsets.UTF_8);
        Variable var1 = variableTxService.createInitializedTx(
                new ByteArrayInputStream(data1), data1.length, currentVarName, null,
                execContextId, "1,2#1", EnumsApi.VariableType.text);
        // Register var1 in states
        ExecContextImpl ec1 = execContextCache.findById(execContextId);
        ExecContextVariableState ecvs1 = execContextVariableStateRepository.findById(ec1.execContextVariableStateId).orElseThrow();
        ExecContextApiData.ExecContextVariableStates info1 = ecvs1.getExecContextVariableStateInfo();
        info1.states.add(makeVariableState("1,2#1", var1.id, currentVarName));
        ecvs1.updateParams(info1);
        execContextVariableStateRepository.save(ecvs1);

        byte[] data2 = "branch-2".getBytes(StandardCharsets.UTF_8);
        Variable var2 = variableTxService.createInitializedTx(
                new ByteArrayInputStream(data2), data2.length, currentVarName, null,
                execContextId, "1,2#2", EnumsApi.VariableType.text);
        ExecContextImpl ec2 = execContextCache.findById(execContextId);
        ExecContextVariableState ecvs2 = execContextVariableStateRepository.findById(ec2.execContextVariableStateId).orElseThrow();
        ExecContextApiData.ExecContextVariableStates info2 = ecvs2.getExecContextVariableStateInfo();
        info2.states.add(makeVariableState("1,2#2", var2.id, currentVarName));
        ecvs2.updateParams(info2);
        execContextVariableStateRepository.save(ecvs2);

        // Search from deep child of branch #1
        Variable found = variableTxService.findVariableInAllInternalContexts(
                currentVarName, "1,2,5|1#0", execContextId);

        assertNotNull(found,
                "Should find a variable when searching from '1,2,5|1#0'");
        assertEquals(var1.id, found.id,
                "Should find branch #1's variable, not #0 or #2. " +
                "Walk from 1,2,5|1#0 → 1,2#1 (exact match for #1).");
    }
}

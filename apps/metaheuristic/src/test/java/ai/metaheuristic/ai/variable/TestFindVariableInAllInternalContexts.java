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
        System.setProperty("mh.home", tempDir.toAbsolutePath().toString());
    }

    @AfterAll
    static void cleanupLogging() {
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
}

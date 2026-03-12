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
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.api.EnumsApi;
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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureCache
@Slf4j
public class TestFindVariableInAllInternalContexts {

    private static final Long EXEC_CONTEXT_ID = 99999L;
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
    @Autowired private TxSupportForTestingService txSupportForTestingService;

    private String currentVarName;

    @BeforeEach
    public void before() {
        currentVarName = "test-var-find-" + counter.incrementAndGet();
    }

    @AfterEach
    public void after() {
        txSupportForTestingService.deleteVariableByName(currentVarName);
    }

    private Variable createVariable(String taskContextId) {
        byte[] data = "ACTIVE".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream is = new ByteArrayInputStream(data);
        return variableTxService.createInitializedTx(is, data.length, currentVarName, null, EXEC_CONTEXT_ID, taskContextId, EnumsApi.VariableType.text);
    }

    /**
     * Baseline: variable at "1,2" should be found from "1,2,5"
     * Walk: "1,2,5" -> "1,2" (found)
     */
    @Test
    public void test_findVariable_atParentContext_simple() {
        Variable created = createVariable("1,2");
        assertNotNull(created);

        Variable found = variableTxService.findVariableInAllInternalContexts(currentVarName, "1,2,5", EXEC_CONTEXT_ID);
        assertNotNull(found, "Variable at '1,2' should be found when searching from '1,2,5'");
        assertEquals(created.id, found.id);
    }

    /**
     * Baseline: variable at "1" should be found from "1,2,5"
     * Walk: "1,2,5" -> "1,2" -> "1" (found)
     */
    @Test
    public void test_findVariable_atGrandparentContext() {
        Variable created = createVariable("1");
        assertNotNull(created);

        Variable found = variableTxService.findVariableInAllInternalContexts(currentVarName, "1,2,5", EXEC_CONTEXT_ID);
        assertNotNull(found, "Variable at '1' should be found when searching from '1,2,5'");
        assertEquals(created.id, found.id);
    }

    /**
     * Variable at exact same context should be found
     */
    @Test
    public void test_findVariable_atSameContext() {
        Variable created = createVariable("1,2,5");
        assertNotNull(created);

        Variable found = variableTxService.findVariableInAllInternalContexts(currentVarName, "1,2,5", EXEC_CONTEXT_ID);
        assertNotNull(found, "Variable at '1,2,5' should be found when searching from '1,2,5'");
        assertEquals(created.id, found.id);
    }

    /**
     * BUG REPRODUCTION: variable at "1,2#1" should be reachable from "1,2,5"
     * but the current walk goes "1,2,5" -> "1,2" -> "1" -> null
     * and never checks "1,2#1".
     *
     * This is the exact scenario from the production bug:
     * - Variable amendmentStatus is created at taskContextId="1,2#1"
     * - Task at taskContextId="1,2,5" tries to find it as input
     * - findVariableInAllInternalContexts returns null
     */
    @Test
    public void test_findVariable_atHashContext_fromSibling_BUG() {
        Variable created = createVariable("1,2#1");
        assertNotNull(created);

        Variable found = variableTxService.findVariableInAllInternalContexts(currentVarName, "1,2,5", EXEC_CONTEXT_ID);

        // This assertion documents the BUG: we expect the variable to be found,
        // but the current implementation returns null because the walk from "1,2,5"
        // goes to "1,2" (no #), not "1,2#1".
        assertNotNull(found,
                "BUG: Variable at '1,2#1' should be found when searching from '1,2,5', " +
                "but deriveParentTaskContextId('1,2,5') returns '1,2' not '1,2#1'. " +
                "The walk path is: 1,2,5 -> 1,2 -> 1 -> null. It never visits 1,2#1.");
    }

    /**
     * Verify that finding a variable at a #-suffixed context works when searching
     * from that exact context.
     */
    @Test
    public void test_findVariable_atHashContext_exact() {
        Variable created = createVariable("1,2#1");
        assertNotNull(created);

        Variable found = variableTxService.findVariableInAllInternalContexts(currentVarName, "1,2#1", EXEC_CONTEXT_ID);
        assertNotNull(found, "Variable at '1,2#1' should be found when searching from '1,2#1'");
        assertEquals(created.id, found.id);
    }

    /**
     * From "1,2#1", the parent walk should go to "1" (strip last comma-component from "1,2").
     * A variable at "1" should be found.
     */
    @Test
    public void test_findVariable_walkFromHashContext_toRoot() {
        Variable created = createVariable("1");
        assertNotNull(created);

        Variable found = variableTxService.findVariableInAllInternalContexts(currentVarName, "1,2#1", EXEC_CONTEXT_ID);
        assertNotNull(found, "Variable at '1' should be found when searching from '1,2#1'");
        assertEquals(created.id, found.id);
    }

    /**
     * Variable at unrelated context should NOT be found.
     */
    @Test
    public void test_findVariable_atUnrelatedContext_notFound() {
        Variable created = createVariable("1,3");
        assertNotNull(created);

        Variable found = variableTxService.findVariableInAllInternalContexts(currentVarName, "1,2,5", EXEC_CONTEXT_ID);
        // Walk from "1,2,5" -> "1,2" -> "1" -> null. Never visits "1,3".
        assertNull(found, "Variable at '1,3' should NOT be found when searching from '1,2,5'");
    }
}

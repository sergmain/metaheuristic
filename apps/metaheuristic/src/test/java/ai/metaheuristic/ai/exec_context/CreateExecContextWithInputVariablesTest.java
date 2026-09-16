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

package ai.metaheuristic.ai.exec_context;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepositoryForTest;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.CommonConsts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Creating an ExecContext for a SourceCode that declares source-level input variables.
 *
 * <p>A SourceCode with inputs cannot have its Tasks produced until those inputs hold something, so
 * before this path existed a REUSABLE workflow - one taking its subject at run time instead of having
 * it written into the .mhsc - could not be launched at all. What is pinned here is that the values
 * arrive, that Tasks are produced against them, and that both halves of the name match are enforced.
 *
 * <p>❗ The undeclared-name case is the one worth having. A missing value fails loudly on its own, but
 * a MIStyped name would otherwise satisfy the missing-check for every other variable and leave the real
 * input uninitialized, surfacing much later as a null variable at whichever Task read it.
 *
 * <p>No doubles, per RULE-NO-MOCKITO.md: the whole behaviour is a real ExecContext, a real variable
 * store and real Task production, none of which a stub can express.
 *
 * @author Serge
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class CreateExecContextWithInputVariablesTest extends PreparingSourceCode {

    @Autowired private ExecContextCreatorTopLevelService execContextCreatorTopLevelServiceLocal;
    @Autowired private VariableTxService variableTxServiceLocal;
    @Autowired private TxSupportForTestingService txSupportForTestingServiceLocal;
    @Autowired private TaskRepositoryForTest taskRepositoryForTestLocal;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang(
                "/source_code/mhsc/exec-context-input-variables-1.0.mhsc", EnumsApi.SourceCodeLang.mhsc, null);
    }

    @Test
    public void test_everyInputSupplied_valuesLandAndTasksAreProduced() {
        final ExecContextCreatorService.ExecContextCreationResult result =
                create(Map.of("alpha", "alpha-value", "beta", "beta-value"));

        assertTrue(result.getErrorMessagesAsList().isEmpty(),
                "PHASE #1: creation must succeed, errors: " + result.getErrorMessagesAsStr());
        assertNotNull(result.execContext, "PHASE #1: the ExecContext must exist");
        setExecContextForTest(result.execContext);

        assertEquals("alpha-value", contentOf("alpha", result.execContext.id),
                "PHASE #2: alpha must hold what the caller passed");
        assertEquals("beta-value", contentOf("beta", result.execContext.id),
                "PHASE #2: beta must hold what the caller passed");

        assertFalse(taskRepositoryForTestLocal.findByExecContextIdAsList(result.execContext.id).isEmpty(),
                "PHASE #3: Tasks must have been produced - initializing the inputs is what unblocks production");
    }

    @Test
    public void test_missingValue_isRefusedAndNamesTheVariable() {
        final ExecContextCreatorService.ExecContextCreationResult result = create(Map.of("alpha", "alpha-value"));

        final String errors = result.getErrorMessagesAsStr();
        assertNull(result.execContext, "PHASE #1: nothing may be left behind when an input was never initialized");
        assertTrue(errors.contains("562.120"), "PHASE #2: expected 562.120, was: " + errors);
        assertTrue(errors.contains("beta"), "PHASE #2: the message must name the uninitialized variable, was: " + errors);
    }

    @Test
    public void test_undeclaredName_isRefusedAndNamesIt() {
        final ExecContextCreatorService.ExecContextCreationResult result =
                create(Map.of("alpha", "alpha-value", "beta", "beta-value", "gamma", "gamma-value"));

        final String errors = result.getErrorMessagesAsStr();
        assertNull(result.execContext, "PHASE #1: a value for an undeclared name is an error, not a warning");
        assertTrue(errors.contains("562.122"), "PHASE #2: expected 562.122, was: " + errors);
        assertTrue(errors.contains("gamma"), "PHASE #2: the message must name the undeclared variable, was: " + errors);
    }

    private ExecContextCreatorService.ExecContextCreationResult create(Map<String, String> inputVariables) {
        final ExecContextApiData.UserExecContext context =
                new ExecContextApiData.UserExecContext(getAccount().id, getCompany().getUniqueId());
        return execContextCreatorTopLevelServiceLocal.createExecContextAndStart(
                getSourceCode().id, context, true, null,
                new ExecContextData.ExecContextCreationInfo("by CreateExecContextWithInputVariablesTest"),
                inputVariables);
    }

    private String contentOf(String name, Long execContextId) {
        final Variable v = txSupportForTestingServiceLocal.findVariableInAllInternalContexts(
                name, CommonConsts.TOP_LEVEL_CONTEXT_ID, execContextId);
        assertNotNull(v, "variable '" + name + "' must exist in the top-level context");
        return variableTxServiceLocal.getVariableDataAsString(v.id);
    }
}
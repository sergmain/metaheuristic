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

package ai.metaheuristic.ai.dispatcher.rest.v1;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The two pure functions behind {@code POST .../exec-context-with-variables-add-commit}: the body's map as the creator
 * takes it, and the creator's result as the endpoint answers it. Spring-less - the endpoint itself is driven for real
 * in {@code ExecContextWithVariablesRestControllerTest}.
 */
@Execution(ExecutionMode.CONCURRENT)
public class ExecContextWithVariablesConversionTest {

    @Test
    public void test_toInputVariables_nullMapIsNoValues() {
        assertEquals(Map.of(), ExecContextRestController.toInputVariables(null));
    }

    @Test
    public void test_toInputVariables_textAndExplicitNull() {
        final Map<String, String> body = new HashMap<>();
        body.put("alpha", "alpha-value");
        body.put("beta", null);
        final Map<String, ExecContextData.VariableValue> values = ExecContextRestController.toInputVariables(body);
        assertEquals(2, values.size(), "an explicit null keeps its name - it is a supplied 'no value', not an absent one");
        assertEquals(new ExecContextData.VariableValue("alpha-value"), values.get("alpha"));
        assertEquals(new ExecContextData.VariableValue(null), values.get("beta"));
        assertTrue(values.containsKey("beta"));
    }

    @Test
    public void test_toResult_createdCarriesIdStateAndUid() {
        final SourceCodeImpl sc = new SourceCodeImpl();
        sc.uid = "test-uid-1.0";
        final ExecContextImpl ec = new ExecContextImpl();
        ec.id = 77L;
        ec.state = EnumsApi.ExecContextState.STARTED.code;
        final ExecContextData.ExecContextWithVariablesResult r =
                ExecContextRestController.toResult(5L, new ExecContextCreatorService.ExecContextCreationResult(sc, ec));
        assertEquals(77L, r.execContextId);
        assertEquals(5L, r.sourceCodeId);
        assertEquals("test-uid-1.0", r.sourceCodeUid);
        assertEquals(EnumsApi.ExecContextState.STARTED.code, r.state);
        assertEquals("STARTED", r.stateName);
        assertFalse(r.isErrorMessages(), "no error for a created ExecContext, was: " + r.getErrorMessagesAsStr());
    }

    @Test
    public void test_toResult_refusalCarriesTheCreatorsErrorsAndNoId() {
        final ExecContextData.ExecContextWithVariablesResult r = ExecContextRestController.toResult(5L,
                new ExecContextCreatorService.ExecContextCreationResult(List.of("562.120 not initialized: beta")));
        assertNull(r.execContextId);
        assertNull(r.state);
        assertEquals(List.of("562.120 not initialized: beta"), r.getErrorMessagesAsList());
    }

    @Test
    public void test_toResult_noExecContextAndNoErrorIsStillAnError() {
        final ExecContextData.ExecContextWithVariablesResult r =
                ExecContextRestController.toResult(5L, new ExecContextCreatorService.ExecContextCreationResult());
        assertNull(r.execContextId);
        assertEquals(1, r.getErrorMessagesAsList().size());
        assertTrue(r.getErrorMessagesAsStr().startsWith("01.571.040"), r.getErrorMessagesAsStr());
    }
}

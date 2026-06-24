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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.exceptions.TaskCreationException;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization of {@link TaskVariableInitTxService#toInputVariable} for an unseeded
 * local source-input. An input declared {@code nullable} but never seeded (e.g. an omitted
 * optional free top-level variable such as {@code topLevelReqCount}) must resolve to an
 * absent input, NOT be rejected. A non-nullable unseeded input remains a hard error (179.120).
 *
 * Pure unit test (no Spring): {@link VariableTxService} is a hand-rolled subclass whose
 * variable lookup always returns null (the unseeded case); every other dependency is null
 * because only the local-context branch is exercised.
 *
 * @author Sergio Lissner
 */
@Execution(ExecutionMode.CONCURRENT)
public class TaskVariableInitTxServiceNullableInputTest {

    /** The unseeded case: variable lookup never finds anything. */
    private static class UnseededVariableTxService extends VariableTxService {
        UnseededVariableTxService() {
            super(null, null, null, null, null, null, null, null, null, null, null, null);
        }
        @Override
        public Object[] findVariableInAllInternalContexts(List<String> taskCtxIds, String variable, Long execContextId) {
            return null;
        }
    }

    private static TaskVariableInitTxService newService() {
        return new TaskVariableInitTxService(new UnseededVariableTxService(), null, null, null, null, null, null, null);
    }

    private static ExecContextParamsYaml.Variable localVar(String name, boolean nullable) {
        ExecContextParamsYaml.Variable v = new ExecContextParamsYaml.Variable();
        v.name = name;
        v.context = EnumsApi.VariableContext.local;
        v.setNullable(nullable);
        return v;
    }

    @Test
    public void test_nullableUnseededLocalInput_isTolerated() {
        TaskVariableInitTxService service = newService();
        ExecContextParamsYaml.Variable v = localVar("topLevelReqCount", true);

        // A nullable, unseeded local input must resolve to an ABSENT input (no throw):
        // id stays null and the resolved input remains nullable.
        TaskParamsYaml.InputVariable iv = assertDoesNotThrow(
                () -> service.toInputVariable(List.of("1"), v, "1", 156L));
        assertNull(iv.id, "unseeded nullable input must have no id");
        assertTrue(iv.getNullable(), "resolved input must remain nullable");
        assertEquals("topLevelReqCount", iv.name);
    }

    @Test
    public void test_nonNullableUnseededLocalInput_stillRejected() {
        TaskVariableInitTxService service = newService();
        ExecContextParamsYaml.Variable v = localVar("topLevelReqCount", false);

        TaskCreationException ex = assertThrows(TaskCreationException.class,
                () -> service.toInputVariable(List.of("1"), v, "1", 156L));
        assertTrue(ex.getMessage().contains("179.120"), ex.getMessage());
    }
}

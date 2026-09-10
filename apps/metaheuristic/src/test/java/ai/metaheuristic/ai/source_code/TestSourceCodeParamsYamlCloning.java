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

package ai.metaheuristic.ai.source_code;

import static org.junit.jupiter.api.Assertions.assertFalse;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.Arrays;
import java.lang.reflect.Field;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.commons.utils.MetaUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Serge
 * Date: 1/11/2020
 * Time: 10:38 PM
 */
@Execution(ExecutionMode.CONCURRENT)
public class TestSourceCodeParamsYamlCloning {

    @Test
    public void cloneProcess() {
        SourceCodeParamsYaml.Process p = new SourceCodeParamsYaml.Process();
        p.name = "name";
        p.code = "code";
        p.function = new SourceCodeParamsYaml.FunctionDefForSourceCode("function-code", "function-params", EnumsApi.FunctionExecContext.external, EnumsApi.FunctionRefType.code);
        p.timeoutBeforeTerminate = 120L;

        p.outputs.add( new SourceCodeParamsYaml.Variable("output-code"));
        p.metas.add(Map.of("key", "value"));

        SourceCodeParamsYaml.Process p1 = p.clone();

        assertEquals("name", p1.name);
        assertEquals("code", p1.code);
        assertNotNull(p1.function);
        assertEquals("function-code", p1.function.code);
        assertEquals("function-params", p1.function.params);

        assertNotNull(p1.timeoutBeforeTerminate);
        assertEquals(120L, (long)p1.timeoutBeforeTerminate);

        assertNotNull(p1.outputs);
        assertEquals(1, p1.outputs.size());
        SourceCodeParamsYaml.Variable params = p1.outputs.get(0);

        assertEquals(EnumsApi.DataSourcing.dispatcher, params.getSourcing());
        assertEquals("output-code", params.name);

        assertNotNull(p.metas);
        assertEquals(1, p1.metas.size());
        assertEquals("value", Objects.requireNonNull(MetaUtils.getMeta(p1.metas, "key")).getValue());
    }

    /**
     * pre/post Functions were dropped. Keeping them on this class as empty lists was not harmless:
     * every serialized SourceCode emitted "preFunctions: []", which then failed to deserialize into
     * ExecContextParamsYaml$Process - that class dropped the field first - with
     * "560.180 ... does not have member field 'java.util.List preFunctions'", killing SourceCode
     * creation for every test that builds an ExecContext.
     *
     * <p>The field must therefore be ABSENT, not merely empty. Versioned classes V1..V6 keep theirs;
     * this asserts only on the version-less one.
     */
    @Test
    public void processHasNoPreOrPostFunctionFields() {
        final Set<String> names = Arrays.stream(SourceCodeParamsYaml.Process.class.getFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertFalse(names.contains("preFunctions"),
                "SourceCodeParamsYaml.Process must not declare preFunctions - an empty list still "
                        + "serializes and breaks ExecContextParamsYaml$Process deserialization");
        assertFalse(names.contains("postFunctions"),
                "SourceCodeParamsYaml.Process must not declare postFunctions - same reason");
    }
}

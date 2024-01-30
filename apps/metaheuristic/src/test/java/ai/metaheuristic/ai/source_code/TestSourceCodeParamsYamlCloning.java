/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.commons.utils.MetaUtils;

import org.junit.jupiter.api.Test;

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
public class TestSourceCodeParamsYamlCloning {

    @Test
    public void cloneProcess() {
        SourceCodeParamsYaml.Process p = new SourceCodeParamsYaml.Process();
        p.name = "name";
        p.code = "code";
        p.function = new SourceCodeParamsYaml.FunctionDefForSourceCode("function-code", "function-params", EnumsApi.FunctionExecContext.external, EnumsApi.FunctionRefType.code);
        p.preFunctions = List.of(
                new SourceCodeParamsYaml.FunctionDefForSourceCode("pre1-code", "pre1-params", EnumsApi.FunctionExecContext.external, EnumsApi.FunctionRefType.code),
                new SourceCodeParamsYaml.FunctionDefForSourceCode("pre2-code", "pre2-params", EnumsApi.FunctionExecContext.external, EnumsApi.FunctionRefType.code)
        );
        p.postFunctions = List.of(
                new SourceCodeParamsYaml.FunctionDefForSourceCode("post1-code", "post1-params", EnumsApi.FunctionExecContext.external, EnumsApi.FunctionRefType.code),
                new SourceCodeParamsYaml.FunctionDefForSourceCode("post2-code", "post2-params", EnumsApi.FunctionExecContext.external, EnumsApi.FunctionRefType.code),
                new SourceCodeParamsYaml.FunctionDefForSourceCode("post3-code", "post3-params", EnumsApi.FunctionExecContext.external, EnumsApi.FunctionRefType.code)
        ) ;

        p.timeoutBeforeTerminate = 120L;

        p.outputs.add( new SourceCodeParamsYaml.Variable("output-code"));
        p.metas.add(Map.of("key", "value"));

        SourceCodeParamsYaml.Process p1 = p.clone();

        assertEquals("name", p1.name);
        assertEquals("code", p1.code);
        assertNotNull(p1.function);
        assertEquals("function-code", p1.function.code);
        assertEquals("function-params", p1.function.params);

        assertNotNull(p1.preFunctions);
        assertEquals(2, p1.preFunctions.size());
        assertEquals("pre1-code", p1.preFunctions.get(0).code);
        assertEquals("pre1-params", p1.preFunctions.get(0).params);
        assertEquals("pre2-code", p1.preFunctions.get(1).code);
        assertEquals("pre2-params", p1.preFunctions.get(1).params);

        assertNotNull(p1.postFunctions);
        assertEquals(3, p1.postFunctions.size());

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
}

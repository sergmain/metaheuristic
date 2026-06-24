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

package ai.metaheuristic.commons.graph.source_code_graph;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.SourceCodeGraph;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The '?' nullable shorthand must be honored on TOP-LEVEL {@code variables { }} inputs, not only on
 * process inputs. {@code varDefToVariable} (top-level) previously ignored '?' while
 * {@code varDefToExecVariable} (process) honored it, so a source-level {@code <- x?} parsed as
 * non-nullable — which made the whole nullable-input pipeline fail downstream.
 *
 * @author Sergio Lissner
 */
@Execution(ExecutionMode.CONCURRENT)
public class TestMhscNullableInputShorthand {

    private static final String SRC = """
            source "test-nullable-shorthand-1.0.0" (strict) {
                variables {
                    <- shorthandNullable?
                    <- plainRequired
                }
                p1 := internal mh.nop {
                    <- shorthandNullable?
                    timeout 60
                }
            }
            """;

    private static ExecContextParamsYaml.Variable input(SourceCodeGraph scg, String name) {
        return scg.variables.inputs.stream().filter(v -> name.equals(v.name)).findFirst().orElse(null);
    }

    @Test
    public void test_topLevelInput_questionMarkShorthand_isNullable() {
        SourceCodeGraph scg = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, SRC);
        ExecContextParamsYaml.Variable v = input(scg, "shorthandNullable");
        assertNotNull(v, "top-level input 'shorthandNullable' must be present");
        assertEquals(Boolean.TRUE, v.getNullable(), "'?' shorthand on a top-level input must mark it nullable");
    }

    @Test
    public void test_topLevelInput_plain_isNotNullable() {
        SourceCodeGraph scg = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, SRC);
        ExecContextParamsYaml.Variable v = input(scg, "plainRequired");
        assertNotNull(v);
        assertNotEquals(Boolean.TRUE, v.getNullable(), "a plain top-level input must not be nullable");
    }
}

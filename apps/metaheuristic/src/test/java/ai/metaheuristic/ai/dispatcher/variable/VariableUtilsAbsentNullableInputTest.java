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

package ai.metaheuristic.ai.dispatcher.variable;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.CommonConsts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full coverage of {@link VariableUtils#initAbsentNullableInputVariables}.
 *
 * A declared nullable INPUT variable that nothing provides must get a nullified (null-blob) row
 * created BEFORE tasks are produced — this is what lets the rest of the pipeline (cache and
 * non-cache) treat it like every other nullable variable (a real id pointing at a null blob).
 * Non-nullable inputs, already-seeded inputs, and global inputs must be left untouched.
 *
 * @author Sergio Lissner
 */
@Execution(ExecutionMode.CONCURRENT)
public class VariableUtilsAbsentNullableInputTest {

    private static ExecContextParamsYaml.Variable input(String name, EnumsApi.VariableContext ctx, boolean nullable) {
        ExecContextParamsYaml.Variable v = new ExecContextParamsYaml.Variable();
        v.name = name;
        v.context = ctx;
        v.setNullable(nullable);
        return v;
    }

    @Test
    public void test_nullableMissingLocalInput_createsNullRow() {
        Set<String> existing = new HashSet<>();
        List<String> created = new ArrayList<>();
        VariableUtils.VariableResolver finder = (name, ctx) -> existing.contains(name) ? 100L : null;
        VariableUtils.VariableCreator creator = (name, ctx) -> { created.add(name + "@" + ctx); return 500L; };

        VariableUtils.initAbsentNullableInputVariables(
                List.of(input("myNullableInput", EnumsApi.VariableContext.local, true)),
                CommonConsts.TOP_LEVEL_CONTEXT_ID, finder, creator);

        assertEquals(List.of("myNullableInput@" + CommonConsts.TOP_LEVEL_CONTEXT_ID), created);
    }

    @Test
    public void test_nullableMissingArrayInput_createsNullRow() {
        List<String> created = new ArrayList<>();
        VariableUtils.VariableResolver finder = (name, ctx) -> null;
        VariableUtils.VariableCreator creator = (name, ctx) -> { created.add(name); return 500L; };

        VariableUtils.initAbsentNullableInputVariables(
                List.of(input("arr", EnumsApi.VariableContext.array, true)),
                CommonConsts.TOP_LEVEL_CONTEXT_ID, finder, creator);

        assertEquals(List.of("arr"), created);
    }

    @Test
    public void test_nullablePresentInput_skipsCreation() {
        Set<String> existing = new HashSet<>(Set.of("myNullableInput"));
        List<String> created = new ArrayList<>();
        VariableUtils.VariableResolver finder = (name, ctx) -> existing.contains(name) ? 100L : null;
        VariableUtils.VariableCreator creator = (name, ctx) -> { created.add(name); return 500L; };

        VariableUtils.initAbsentNullableInputVariables(
                List.of(input("myNullableInput", EnumsApi.VariableContext.local, true)),
                CommonConsts.TOP_LEVEL_CONTEXT_ID, finder, creator);

        assertTrue(created.isEmpty(), "an already-seeded input must not be recreated");
    }

    @Test
    public void test_nonNullableMissingInput_skipsCreation() {
        List<String> created = new ArrayList<>();
        VariableUtils.VariableResolver finder = (name, ctx) -> null;
        VariableUtils.VariableCreator creator = (name, ctx) -> { created.add(name); return 500L; };

        VariableUtils.initAbsentNullableInputVariables(
                List.of(input("required", EnumsApi.VariableContext.local, false)),
                CommonConsts.TOP_LEVEL_CONTEXT_ID, finder, creator);

        assertTrue(created.isEmpty(), "a non-nullable input must be left to fail later, not auto-created");
    }

    @Test
    public void test_globalNullableMissingInput_skipsCreation() {
        List<String> created = new ArrayList<>();
        VariableUtils.VariableResolver finder = (name, ctx) -> null;
        VariableUtils.VariableCreator creator = (name, ctx) -> { created.add(name); return 500L; };

        VariableUtils.initAbsentNullableInputVariables(
                List.of(input("globalThing", EnumsApi.VariableContext.global, true)),
                CommonConsts.TOP_LEVEL_CONTEXT_ID, finder, creator);

        assertTrue(created.isEmpty(), "global inputs are managed separately and must not be auto-created");
    }

    @Test
    public void test_mixedInputs_createsOnlyAbsentNullableNonGlobals() {
        Set<String> existing = new HashSet<>(Set.of("seededNullable"));
        List<String> created = new ArrayList<>();
        VariableUtils.VariableResolver finder = (name, ctx) -> existing.contains(name) ? 100L : null;
        VariableUtils.VariableCreator creator = (name, ctx) -> { created.add(name); return 500L; };

        VariableUtils.initAbsentNullableInputVariables(
                List.of(
                        input("absentNullable", EnumsApi.VariableContext.local, true),
                        input("seededNullable", EnumsApi.VariableContext.local, true),
                        input("required", EnumsApi.VariableContext.local, false),
                        input("globalNullable", EnumsApi.VariableContext.global, true),
                        input("absentNullableArray", EnumsApi.VariableContext.array, true)
                ),
                CommonConsts.TOP_LEVEL_CONTEXT_ID, finder, creator);

        assertEquals(List.of("absentNullable", "absentNullableArray"), created);
    }

    @Test
    public void test_emptyInputs_noop() {
        List<String> created = new ArrayList<>();
        VariableUtils.initAbsentNullableInputVariables(
                List.of(), CommonConsts.TOP_LEVEL_CONTEXT_ID,
                (name, ctx) -> null, (name, ctx) -> { created.add(name); return 500L; });
        assertTrue(created.isEmpty());
    }
}

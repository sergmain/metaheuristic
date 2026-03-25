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

import ai.metaheuristic.ai.exceptions.VariableImmutabilityException;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.exceptions.CommonRollbackException;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Characterization test for variable immutability.
 *
 * Current behavior (buggy): a subprocess can create an output variable with the same name
 * as a variable in an outer (parent) taskContextId. This effectively shadows/reassigns
 * the outer variable for any subsequent subprocess, which is undesirable.
 *
 * The test documents the current behavior first (Green), then will be flipped to assert
 * the correct behavior (variables are immutable by default).
 *
 * @author Sergio Lissner
 * Date: 3/24/2026
 */
@Execution(CONCURRENT)
public class TestVariableImmutability {

    private final AtomicLong variableIdSeq = new AtomicLong(100);
    private final Map<String, Long> existingVariables = new HashMap<>();

    private Long findVariable(String name, String contextId) {
        return existingVariables.get(name + "|" + contextId);
    }

    private Long createVariable(String name, String contextId) {
        Long id = variableIdSeq.incrementAndGet();
        existingVariables.put(name + "|" + contextId, id);
        return id;
    }

    private static ExecContextParamsYaml.Variable processOutput(String name) {
        ExecContextParamsYaml.Variable v = new ExecContextParamsYaml.Variable(name);
        v.ext = ".txt";
        v.type = "test-type";
        return v;
    }

    /**
     * Characterization test: flipped to assert the correct/desired behavior.
     *
     * Scenario:
     * - Parent process at taskContextId "1,2" creates output variable "count" (immutable by default)
     * - Subprocess at taskContextId "1,2,3" also tries to create output variable "count"
     *
     * Desired behavior: the subprocess init should throw an exception because the variable
     * "count" already exists in an outer context and is not declared as mutable.
     */
    @Test
    public void test_subprocess_can_shadow_parent_variable_currentBehavior() {
        // Parent process creates output variable "count" at context "1,2"
        String parentContextId = "1,2";
        List<ExecContextParamsYaml.Variable> parentOutputs = List.of(processOutput("count"));
        List<TaskParamsYaml.OutputVariable> parentTaskOutputs = new ArrayList<>();

        VariableUtils.initOutputVariables(parentOutputs, parentTaskOutputs, parentContextId, this::findVariable, this::createVariable);

        assertEquals(1, parentTaskOutputs.size());
        assertEquals("count", parentTaskOutputs.getFirst().name);
        Long parentVarId = parentTaskOutputs.getFirst().id;
        assertNotNull(parentVarId);

        // Subprocess creates output variable "count" at context "1,2,3" (child of "1,2")
        String childContextId = "1,2,3";
        List<ExecContextParamsYaml.Variable> childOutputs = List.of(processOutput("count"));
        List<TaskParamsYaml.OutputVariable> childTaskOutputs = new ArrayList<>();

        // Desired behavior: this should throw because "count" in parent context is immutable
        assertThrows(VariableImmutabilityException.class, () ->
                VariableUtils.initOutputVariables(childOutputs, childTaskOutputs, childContextId, this::findVariable, this::createVariable)
        );
    }

    /**
     * When a subprocess output variable is declared with mutable=true, it IS allowed to
     * shadow a variable with the same name in a parent context.
     */
    @Test
    public void test_mutable_variable_allows_subprocess_shadow() {
        // Parent process creates output variable "count" at context "1,2"
        String parentContextId = "1,2";
        List<ExecContextParamsYaml.Variable> parentOutputs = List.of(processOutput("count"));
        List<TaskParamsYaml.OutputVariable> parentTaskOutputs = new ArrayList<>();

        VariableUtils.initOutputVariables(parentOutputs, parentTaskOutputs, parentContextId, this::findVariable, this::createVariable);

        assertEquals(1, parentTaskOutputs.size());
        Long parentVarId = parentTaskOutputs.getFirst().id;

        // Subprocess creates output variable "count" at context "1,2,3" with mutable=true
        String childContextId = "1,2,3";
        ExecContextParamsYaml.Variable mutableOutput = processOutput("count");
        mutableOutput.mutable = true;
        List<ExecContextParamsYaml.Variable> childOutputs = List.of(mutableOutput);
        List<TaskParamsYaml.OutputVariable> childTaskOutputs = new ArrayList<>();

        // This should succeed because the child variable is declared mutable
        assertDoesNotThrow(() ->
                VariableUtils.initOutputVariables(childOutputs, childTaskOutputs, childContextId, this::findVariable, this::createVariable)
        );

        assertEquals(1, childTaskOutputs.size());
        assertEquals("count", childTaskOutputs.getFirst().name);
        assertNotEquals(parentVarId, childTaskOutputs.getFirst().id);
    }

    /**
     * When parent and child have different variable names, there should be no conflict
     * even without mutable flag.
     */
    @Test
    public void test_different_variable_names_no_conflict() {
        String parentContextId = "1,2";
        List<ExecContextParamsYaml.Variable> parentOutputs = List.of(processOutput("result"));
        List<TaskParamsYaml.OutputVariable> parentTaskOutputs = new ArrayList<>();
        VariableUtils.initOutputVariables(parentOutputs, parentTaskOutputs, parentContextId, this::findVariable, this::createVariable);

        String childContextId = "1,2,3";
        List<ExecContextParamsYaml.Variable> childOutputs = List.of(processOutput("otherVar"));
        List<TaskParamsYaml.OutputVariable> childTaskOutputs = new ArrayList<>();

        assertDoesNotThrow(() ->
                VariableUtils.initOutputVariables(childOutputs, childTaskOutputs, childContextId, this::findVariable, this::createVariable)
        );
        assertEquals(1, childTaskOutputs.size());
        assertEquals("otherVar", childTaskOutputs.getFirst().name);
    }

    /**
     * Step 2 (Red - Flipped): asserts the DESIRED caller-level behavior.
     *
     * When initOutputVariables throws VariableImmutabilityException, the caller should
     * catch it and record an error state for the task (not let it escape silently).
     *
     * This test simulates the fixed caller pattern: VariableImmutabilityException is caught
     * alongside CommonRollbackException, and an error state is recorded.
     */
    @Test
    public void test_caller_pattern_desiredBehavior_exception_caught_and_error_recorded() {
        // Setup: parent creates "amendmentStatus" at context "1,2#1"
        String parentContextId = "1,2#1";
        List<ExecContextParamsYaml.Variable> parentOutputs = List.of(processOutput("amendmentStatus"));
        List<TaskParamsYaml.OutputVariable> parentTaskOutputs = new ArrayList<>();
        VariableUtils.initOutputVariables(parentOutputs, parentTaskOutputs, parentContextId, this::findVariable, this::createVariable);

        // Child at context "1,2,3,4|1|0#0" tries to create "amendmentStatus" (immutable by default)
        String childContextId = "1,2,3,4|1|0#0";
        List<ExecContextParamsYaml.Variable> childOutputs = List.of(processOutput("amendmentStatus"));
        List<TaskParamsYaml.OutputVariable> childTaskOutputs = new ArrayList<>();

        // Simulate the FIXED caller pattern:
        // both CommonRollbackException and VariableImmutabilityException are caught,
        // and error state is recorded for the task
        AtomicReference<String> errorMessage = new AtomicReference<>(null);
        boolean errorStateRecorded = false;

        try {
            VariableUtils.initOutputVariables(childOutputs, childTaskOutputs, childContextId, this::findVariable, this::createVariable);
        } catch (CommonRollbackException e) {
            // benign rollback
        } catch (VariableImmutabilityException e) {
            errorMessage.set(e.getMessage());
            // DESIRED: the caller catches this and transitions the task to ERROR
            errorStateRecorded = true;
        }

        // Desired behavior: the error was caught AND the task error state was recorded
        assertNotNull(errorMessage.get(), "VariableImmutabilityException should have been thrown");
        assertTrue(errorMessage.get().contains("171.863"), "Error message should contain the immutability error code");
        assertTrue(errorStateRecorded, "Error state should have been recorded for the task");
    }
}

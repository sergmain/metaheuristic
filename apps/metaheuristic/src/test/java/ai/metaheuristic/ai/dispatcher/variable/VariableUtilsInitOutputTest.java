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

package ai.metaheuristic.ai.dispatcher.variable;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sergio Lissner
 * Date: 3/4/2026
 * Time: 5:00 PM
 */
public class VariableUtilsInitOutputTest {

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

    // first init — no existing variables, empty task outputs → outputs get populated
    @Test
    public void test_firstInit_outputsCreated() {
        List<ExecContextParamsYaml.Variable> processOutputs = List.of(
                processOutput("objectiveResult2")
        );
        List<TaskParamsYaml.OutputVariable> taskOutputs = new ArrayList<>();
        String taskContextId = "1,2,5,6,7|1|1|0#0";

        VariableUtils.initOutputVariables(processOutputs, taskOutputs, taskContextId, this::findVariable, this::createVariable);

        assertEquals(1, taskOutputs.size());
        assertEquals("objectiveResult2", taskOutputs.getFirst().name);
        assertNotNull(taskOutputs.getFirst().id);
    }

    // second init after reset — variable exists in DB, task outputs empty → outputs get populated with existing variable ID
    @Test
    public void test_secondInit_variableExistsInDb_outputsPopulated() {
        String taskContextId = "1,2,5,6,7|1|1|0#0";
        // simulate variable created during first pass
        existingVariables.put("objectiveResult2|" + taskContextId, 42L);

        List<ExecContextParamsYaml.Variable> processOutputs = List.of(
                processOutput("objectiveResult2")
        );
        List<TaskParamsYaml.OutputVariable> taskOutputs = new ArrayList<>();

        VariableUtils.initOutputVariables(processOutputs, taskOutputs, taskContextId, this::findVariable, this::createVariable);

        assertEquals(1, taskOutputs.size());
        assertEquals("objectiveResult2", taskOutputs.getFirst().name);
        assertEquals(42L, taskOutputs.getFirst().id);
    }

    // reproduces the multiplication bug: calling initOutputVariables twice on the same task
    // should NOT duplicate outputs
    @Test
    public void test_doubleInit_noOutputDuplication() {
        List<ExecContextParamsYaml.Variable> processOutputs = List.of(
                processOutput("objectiveResult2"),
                processOutput("cascadeResult2")
        );
        List<TaskParamsYaml.OutputVariable> taskOutputs = new ArrayList<>();
        String taskContextId = "1,2,5";

        // first init
        VariableUtils.initOutputVariables(processOutputs, taskOutputs, taskContextId, this::findVariable, this::createVariable);
        assertEquals(2, taskOutputs.size());

        Long firstId = taskOutputs.stream().filter(o -> o.name.equals("objectiveResult2")).findFirst().orElseThrow().id;
        Long secondId = taskOutputs.stream().filter(o -> o.name.equals("cascadeResult2")).findFirst().orElseThrow().id;

        // second init (simulates reset scenario — variables now exist in DB, and task outputs already populated)
        VariableUtils.initOutputVariables(processOutputs, taskOutputs, taskContextId, this::findVariable, this::createVariable);

        // outputs must NOT have duplicated
        assertEquals(2, taskOutputs.size());
        assertEquals(firstId, taskOutputs.stream().filter(o -> o.name.equals("objectiveResult2")).findFirst().orElseThrow().id);
        assertEquals(secondId, taskOutputs.stream().filter(o -> o.name.equals("cascadeResult2")).findFirst().orElseThrow().id);
    }

    // reset scenario: task outputs were cleared but DB variables still exist
    @Test
    public void test_resetWithClearedOutputs_variablesReused() {
        String taskContextId = "1,2,3";

        // first pass: create variables
        List<ExecContextParamsYaml.Variable> processOutputs = List.of(
                processOutput("objectiveResult2")
        );
        List<TaskParamsYaml.OutputVariable> taskOutputs = new ArrayList<>();
        VariableUtils.initOutputVariables(processOutputs, taskOutputs, taskContextId, this::findVariable, this::createVariable);
        assertEquals(1, taskOutputs.size());
        Long originalId = taskOutputs.getFirst().id;

        // simulate reset: clear task outputs but DB variable still exists
        taskOutputs.clear();

        // second pass
        VariableUtils.initOutputVariables(processOutputs, taskOutputs, taskContextId, this::findVariable, this::createVariable);
        assertEquals(1, taskOutputs.size());
        // must reuse existing variable, not create new one
        assertEquals(originalId, taskOutputs.getFirst().id);
    }

    // multiple outputs — only missing ones get added
    @Test
    public void test_partialOutputs_onlyMissingAdded() {
        String taskContextId = "1,2";

        List<ExecContextParamsYaml.Variable> processOutputs = List.of(
                processOutput("var1"),
                processOutput("var2"),
                processOutput("var3")
        );

        // task already has var1 and var3
        List<TaskParamsYaml.OutputVariable> taskOutputs = new ArrayList<>();
        taskOutputs.add(new TaskParamsYaml.OutputVariable(
                10L, EnumsApi.VariableContext.local, "var1", EnumsApi.DataSourcing.dispatcher, null, null,
                null, false, "test-type", true, false, ".txt"
        ));
        taskOutputs.add(new TaskParamsYaml.OutputVariable(
                30L, EnumsApi.VariableContext.local, "var3", EnumsApi.DataSourcing.dispatcher, null, null,
                null, false, "test-type", true, false, ".txt"
        ));

        VariableUtils.initOutputVariables(processOutputs, taskOutputs, taskContextId, this::findVariable, this::createVariable);

        assertEquals(3, taskOutputs.size());
        // var1 and var3 unchanged
        assertEquals(10L, taskOutputs.stream().filter(o -> o.name.equals("var1")).findFirst().orElseThrow().id);
        assertEquals(30L, taskOutputs.stream().filter(o -> o.name.equals("var3")).findFirst().orElseThrow().id);
        // var2 was added
        assertNotNull(taskOutputs.stream().filter(o -> o.name.equals("var2")).findFirst().orElseThrow().id);
    }
}

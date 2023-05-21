/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.mhbp.scenario;

import ai.metaheuristic.ai.dispatcher.internal_functions.aggregate.AggregateFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.batch_line_splitter.BatchLineSplitterFunction;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationUtils;
import ai.metaheuristic.ai.mhbp.beans.Scenario;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.commons.utils.MetaUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static ai.metaheuristic.ai.mhbp.scenario.ScenarioUtils.getVariables;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sergio Lissner
 * Date: 5/14/2023
 * Time: 2:38 AM
 */
public class ScenarioUtilsTest {

    @Test
    public void test_to_1() throws IOException {
        String yaml = IOUtils.resourceToString("/mhbp/scenario/scenario-fruits.yaml", StandardCharsets.UTF_8);
        Scenario scenario = new Scenario();
        scenario.id = 1L;
        scenario.version=1;
        scenario.scenarioGroupId = 5L;
        scenario.name = "Fruit production";
        scenario.setParams(yaml);


        // main function for testing
        SourceCodeParamsYaml sc = ScenarioUtils.to(scenario);



        String result = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.toString(sc);
        System.out.println(result);

        assertNull(SourceCodeValidationUtils.validateSourceCodeParamsYaml(SourceCodeValidationUtils.NULL_CHECK_FUNC, sc));

        assertEquals(4, sc.source.processes.size());
        final SourceCodeParamsYaml.SubProcesses subProcesses = sc.source.processes.get(2).subProcesses;
        assertNotNull(subProcesses);
        assertFalse(subProcesses.processes.isEmpty());
        assertEquals(2, subProcesses.processes.size());

        assertEquals(0, sc.source.processes.get(0).inputs.size());

        assertEquals(0, sc.source.processes.get(2).inputs.size());
        assertEquals(0, sc.source.processes.get(2).outputs.size());

        final Meta apiCode = MetaUtils.getMeta(sc.source.processes.get(0).getMetas(), "apiCode");
        assertNotNull(apiCode);
        assertEquals("simple", apiCode.getValue());

        final Meta varForSplitting = MetaUtils.getMeta(sc.source.processes.get(2).getMetas(), BatchLineSplitterFunction.VARIABLE_FOR_SPLITTING);
        assertNotNull(varForSplitting);
        assertEquals("list_of_fruits", varForSplitting.getValue());

        final Meta outputVariable = MetaUtils.getMeta(sc.source.processes.get(2).getMetas(), BatchLineSplitterFunction.OUTPUT_VARIABLE);
        assertNotNull(outputVariable);
        assertEquals("fruit", outputVariable.getValue());

        final Meta outputVariableForAggregate = MetaUtils.getMeta(sc.source.processes.get(3).getMetas(), AggregateFunction.VARIABLES);
        assertNotNull(outputVariableForAggregate);
        assertEquals("intro, fruit, fruit description, best consumer", outputVariableForAggregate.getValue());
    }

    @Test
    public void test_getVariables() {
        String text = "Hello, my name is [[firstName]] and my last name is {{lastName}}.";
        var list = getVariables(text, false);
        assertEquals(2, list.size());
        assertTrue(list.contains("firstName"));
        assertTrue(list.contains("lastName"));

        list = getVariables(text, true);
        assertEquals(2, list.size());
        assertTrue(list.contains("firstName"));
        assertTrue(list.contains("lastName"));

        list = getVariables("Hello", false);
        assertEquals(0, list.size());

        list = getVariables("Hello", true);
        assertEquals(1, list.size());
        assertTrue(list.contains("Hello"));
    }

    @Test
    public void test_getVariables_1() {
        String text = "List of fruits which can be grown in US consist of following:\n" +
                      "[[list of fruits]]";
        var list = getVariables(text, false);
        assertEquals(1, list.size());
        assertTrue(list.contains("list of fruits"));

    }
}
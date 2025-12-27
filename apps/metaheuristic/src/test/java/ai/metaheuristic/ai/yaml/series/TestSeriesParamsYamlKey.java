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

package ai.metaheuristic.ai.yaml.series;

import ai.metaheuristic.commons.utils.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Serge
 * Date: 4/3/2021
 * Time: 8:36 PM
 */
@Execution(CONCURRENT)
public class TestSeriesParamsYamlKey {

    @Test
    public void test1() throws JsonProcessingException {
        SeriesParamsYaml params = new SeriesParamsYaml();

        SeriesParamsYaml.ExperimentPart part = new SeriesParamsYaml.ExperimentPart();
        part.taskContextId = "1#1";
        part.hyperParams.putAll(Map.of("k1","v1","k2","v2","k3","v3","k4","v4"));
        part.variables.addAll(List.of("var10", "var1", "var6", "var3"));

        params.parts.add(part);

        SeriesParamsYaml.ExperimentPart part2 = new SeriesParamsYaml.ExperimentPart();
        part2.taskContextId = "1#2";
        part2.hyperParams.putAll(Map.of("k21","v21","k22","v22","k23","v23","k24","v24"));
        part2.variables.addAll(List.of("var210", "var21", "var26", "var23"));

        params.parts.add(part2);

        String yaml = SeriesParamsYamlUtils.BASE_YAML_UTILS.toString(params);
        System.out.println(yaml);
        System.out.println("\n\n\n");

        String json = JsonUtils.getMapper().writeValueAsString(params);
        System.out.println(json);

        SeriesParamsYaml params21 = JsonUtils.getMapper().readValue(json, SeriesParamsYaml.class);


        SeriesParamsYaml params1 = SeriesParamsYamlUtils.BASE_YAML_UTILS.to(yaml);

        System.out.println(yaml);

        SeriesParamsYaml.ExperimentPart part3 = new SeriesParamsYaml.ExperimentPart();
        part3.taskContextId = "1#2";
        part3.hyperParams.putAll(Map.of("k21","v21","k22","v22","k23","v23","k24","v24"));
        part3.variables.addAll(List.of("var210", "var21", "var26", "var23"));


        assertTrue(params1.parts.contains(part3));
        assertTrue(params21.parts.contains(part3));

        params1.parts.remove(part3);
        assertFalse(params1.parts.contains(part3));
        assertEquals(1, params1.parts.size());

        SeriesParamsYaml.ExperimentPart part4 = new SeriesParamsYaml.ExperimentPart();
        part4.taskContextId = "1#2";
        part4.hyperParams.putAll(Map.of("k21","v21","k22","v22","k23","v23","k24","v24"));
        part4.variables.addAll(List.of("var210", "var21", "var26"));

        assertFalse(params1.parts.contains(part4));

    }
}

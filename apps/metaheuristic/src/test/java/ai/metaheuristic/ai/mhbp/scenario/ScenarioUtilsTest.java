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

import ai.metaheuristic.ai.mhbp.beans.Scenario;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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

        SourceCodeParamsYaml sc = ScenarioUtils.to(scenario);

        String result = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.toString(sc);

        System.out.println(result);
    }
}

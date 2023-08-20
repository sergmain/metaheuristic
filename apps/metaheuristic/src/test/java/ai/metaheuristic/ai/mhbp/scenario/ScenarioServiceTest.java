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

import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.experiment.ExperimentCache;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepositoryForTest;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationService;
import ai.metaheuristic.ai.mhbp.beans.Scenario;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author Sergio Lissner
 * Date: 5/14/2023
 * Time: 7:05 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
//@ActiveProfiles("dispatcher")
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
public class ScenarioServiceTest {

    @Autowired
    private SourceCodeValidationService sourceCodeValidationService;


/*
    @Test
    public void test_checkConsistencyOfSourceCode() throws IOException {

        String yaml = IOUtils.resourceToString("/mhbp/scenario/scenario-fruits.yaml", StandardCharsets.UTF_8);
        Scenario scenario = new Scenario();
        scenario.id = 1L;
        scenario.version=1;
        scenario.scenarioGroupId = 5L;
        scenario.name = "Fruit production";
        scenario.setParams(yaml);


        SourceCodeParamsYaml scpy = ScenarioUtils.to("uid", scenario.getScenarioParams());
        String yamlSc = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.toString(scpy);
        SourceCodeApiData.SourceCodeResult result = sourceCodeService.createSourceCode(yaml, scpy, 1L);

        SourceCodeImpl sc = new SourceCodeImpl();
        sc.updateParams(scpy);


        var result = sourceCodeValidationService.checkConsistencyOfSourceCode();
    }
*/

}

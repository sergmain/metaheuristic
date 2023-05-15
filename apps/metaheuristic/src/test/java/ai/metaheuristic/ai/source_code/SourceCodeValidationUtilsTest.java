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

package ai.metaheuristic.ai.source_code;

import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationUtils;
import ai.metaheuristic.ai.mhbp.beans.Scenario;
import ai.metaheuristic.ai.mhbp.scenario.ScenarioUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import static ai.metaheuristic.api.EnumsApi.SourceCodeValidateStatus.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Sergio Lissner
 * Date: 5/14/2023
 * Time: 9:21 PM
 */
public class SourceCodeValidationUtilsTest {

    @Test
    public void test_to_variable_error() throws IOException {
        String yaml = IOUtils.resourceToString("/source_code/yaml/variables/error-name-of-input-variable.yaml", StandardCharsets.UTF_8);

        SourceCodeParamsYaml sc = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(yaml);

        Function<SourceCodeParamsYaml.Process, SourceCodeApiData.SourceCodeValidationResult> checkFunctionsFunc =
                (p)-> new SourceCodeApiData.SourceCodeValidationResult(OK, null);

        final SourceCodeApiData.SourceCodeValidationResult actual = SourceCodeValidationUtils.validateSourceCodeParamsYaml(checkFunctionsFunc, sc);

        assertNotNull(actual);
        assertEquals(EnumsApi.SourceCodeValidateStatus.OUTPUT_VARIABLE_NOT_DEFINED_ERROR, actual.status);
    }

}

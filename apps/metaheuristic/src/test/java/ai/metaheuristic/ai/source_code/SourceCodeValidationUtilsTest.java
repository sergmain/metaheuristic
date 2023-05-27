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

import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationUtils.NULL_CHECK_FUNC;
import static ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationUtils.validateSourceCodeParamsYaml;
import static ai.metaheuristic.api.EnumsApi.SourceCodeValidateStatus.OUTPUT_VARIABLE_NOT_DEFINED_ERROR;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Sergio Lissner
 * Date: 5/14/2023
 * Time: 9:21 PM
 */
@Execution(CONCURRENT)
public class SourceCodeValidationUtilsTest {

    @Test
    public void test_doesVariableHaveSource() throws IOException {
        String params = IOUtils.resourceToString("/source_code/yaml/for-testing-exec-source-code-2.yaml", StandardCharsets.UTF_8);
        SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(params);
        sourceCodeParamsYaml.checkIntegrity();

        assertTrue(SourceCodeValidationUtils.doesVariableHaveSource(sourceCodeParamsYaml.source, "var-input-1"));
    }

    @Test
    public void test_to_variable_error() throws IOException {
        String yaml = IOUtils.resourceToString("/source_code/yaml/variables/error-name-of-input-variable.yaml", StandardCharsets.UTF_8);

        SourceCodeParamsYaml sc = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(yaml);

        final SourceCodeApiData.SourceCodeValidationResult actual = validateSourceCodeParamsYaml(NULL_CHECK_FUNC, sc);

        assertNotNull(actual);
        assertEquals(OUTPUT_VARIABLE_NOT_DEFINED_ERROR, actual.status);
    }

    @Test
    public void test_validateSourceCodeParamsYaml() throws IOException {
        String yaml = IOUtils.resourceToString("/source_code/yaml/default-source-code-for-testing.yaml", StandardCharsets.UTF_8);
        SourceCodeParamsYaml scpy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        SourceCodeApiData.SourceCodeValidationResult result = validateSourceCodeParamsYaml(NULL_CHECK_FUNC, scpy);
        assertNull(result);
    }

    @Test
    public void test_validateSourceCodeParamsYaml_1() throws IOException {
        String yaml = IOUtils.resourceToString("/source_code/yaml/default-source-code-for-testing.yaml", StandardCharsets.UTF_8);
        SourceCodeParamsYaml scpy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        scpy.source.variables = new SourceCodeParamsYaml.VariableDefinition();
        assertNull(scpy.source.variables.globals);
        SourceCodeApiData.SourceCodeValidationResult result = validateSourceCodeParamsYaml(NULL_CHECK_FUNC, scpy);
        assertNotNull(result);
        assertEquals(OUTPUT_VARIABLE_NOT_DEFINED_ERROR, result.status);
    }
}

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

package ai.metaheuristic.ai.source_code;

import ai.metaheuristic.commons.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 4/5/2020
 * Time: 4:57 PM
 */
@Execution(ExecutionMode.CONCURRENT)
public class TestSourceCodeParamsYamlParsing {

    @Test
    public void testParsingForBatch() throws IOException {
        String yaml = IOUtils.resourceToString("/source_code/yaml/source-code-for-batch-processing-v1.yaml", StandardCharsets.UTF_8);
        SourceCodeParamsYaml scpy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        scpy.checkIntegrity();
    }

    @Test
    public void testParsingForTextClassification() throws IOException {
        String yaml = IOUtils.resourceToString("/source_code/yaml/source-code-for-text-classification-v1.yaml", StandardCharsets.UTF_8);
        SourceCodeParamsYaml scpy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        scpy.checkIntegrity();
    }

    @Test
    public void testParsingForPreprocessingAndClassification() throws IOException {
        String yaml = IOUtils.resourceToString("/source_code/yaml/source-code-for-preprocessing-and-classification-v1.yaml", StandardCharsets.UTF_8);
        SourceCodeParamsYaml scpy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        scpy.checkIntegrity();
    }

    @Test
    public void testDefaultSourceCodeV1_noDuplicateInputs() throws IOException {
        String yaml = IOUtils.resourceToString("/source_code/yaml/default-source-code-for-testing.yaml", StandardCharsets.UTF_8);
        SourceCodeParamsYaml scpy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        scpy.checkIntegrity();

        // dataset-processing process should have exactly 1 input (assembled-raw-output)
        SourceCodeParamsYaml.Process datasetProcessing = scpy.source.processes.stream()
                .filter(p -> "dataset-processing".equals(p.code))
                .findFirst()
                .orElse(null);

        assertNotNull(datasetProcessing, "dataset-processing process not found");
        System.out.println("dataset-processing inputs: " + datasetProcessing.inputs.size());
        for (var inp : datasetProcessing.inputs) {
            System.out.println("  input: " + inp.name);
        }
        System.out.println("dataset-processing outputs: " + datasetProcessing.outputs.size());
        for (var out : datasetProcessing.outputs) {
            System.out.println("  output: " + out.name);
        }
        assertEquals(1, datasetProcessing.inputs.size(), "dataset-processing should have 1 input");
        assertEquals("assembled-raw-output", datasetProcessing.inputs.get(0).name);
        assertEquals(1, datasetProcessing.outputs.size(), "dataset-processing should have 1 output");
        assertEquals("dataset-processing-output", datasetProcessing.outputs.get(0).name);
    }

}

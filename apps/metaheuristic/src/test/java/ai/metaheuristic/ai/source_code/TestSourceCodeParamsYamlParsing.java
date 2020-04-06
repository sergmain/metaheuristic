/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author Serge
 * Date: 4/5/2020
 * Time: 4:57 PM
 */
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

}

/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;

@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
@ActiveProfiles("launchpad")
public class TestProcessMeta {

    @Test
    public void testProcessMeta() {
        SourceCodeParamsYaml.SourceCodeYaml sourceCodeYaml = new SourceCodeParamsYaml.SourceCodeYaml();
        sourceCodeYaml.uid = "test-process-for-meta";
        {
            SourceCodeParamsYaml.Process p = new SourceCodeParamsYaml.Process();
            p.name = "experiment";
            p.code = "test-experiment-code-01";
            p.output.add(new SourceCodeParamsYaml.Variable("model"));

            p.metas.addAll(
                    Arrays.asList(
                            new Meta("assembled-raw", "assembled-raw", null),
                            new Meta("dataset", "dataset-processing", null),
                            new Meta("feature", "feature", null)
                    )
            );

            sourceCodeYaml.processes.add(p);
        }
        SourceCodeParamsYaml sourceCodeParamsYaml = new SourceCodeParamsYaml();
        sourceCodeParamsYaml.source = sourceCodeYaml;

        String s = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.toString(sourceCodeParamsYaml);

        System.out.println(s);

        SourceCodeParamsYaml planParams = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(s);
        SourceCodeParamsYaml.SourceCodeYaml yaml1 = planParams.source;

        Assert.assertEquals(sourceCodeYaml, yaml1);

    }

}

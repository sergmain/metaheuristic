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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;

import static ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml.SourceCodeYaml;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
public class TestProcess {

    @Test
    public void testProcessMeta() {
        SourceCodeParamsYaml.Process p = new SourceCodeParamsYaml.Process();
        p.function = new SourceCodeParamsYaml.FunctionDefForSourceCode("some-function:1.0");

        p.metas.addAll(
                Arrays.asList(
                        new Meta("assembled-raw", "assembled-raw", null),
                        new Meta("dataset", "dataset-processing", null),
                        new Meta("feature", "feature", null)
                )
        );
        SourceCodeParamsYaml sourceCodeParamsYaml = new SourceCodeParamsYaml();
        SourceCodeYaml sourceCodeYaml = new SourceCodeParamsYaml.SourceCodeYaml();
        sourceCodeYaml.uid = "test-process-for-meta";
        sourceCodeYaml.processes.add(p);
        sourceCodeParamsYaml.source = sourceCodeYaml;

        String s = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.toString(sourceCodeParamsYaml);
        SourceCodeParamsYaml planParams = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(s);
        SourceCodeYaml sourceCodeYamlV21 = planParams.source;

        SourceCodeParamsYaml.Process p1 = sourceCodeYamlV21.getProcesses().get(0);

        assertNotNull(p.getMeta("dataset"));
        assertEquals("dataset-processing", p.getMeta("dataset").getValue());

        assertNotNull(p.getMeta("assembled-raw"));
        assertEquals("assembled-raw", p.getMeta("assembled-raw").getValue());

        assertNotNull(p.getMeta("feature"));
        assertEquals("feature", p.getMeta("feature").getValue());
    }

}

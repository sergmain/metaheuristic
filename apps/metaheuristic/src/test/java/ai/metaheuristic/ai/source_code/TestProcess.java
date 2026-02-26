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
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.commons.utils.MetaUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml.SourceCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Execution(ExecutionMode.CONCURRENT)
public class TestProcess {

    @Test
    public void testProcessMeta() {
        SourceCodeParamsYaml.Process p = new SourceCodeParamsYaml.Process();
        p.function = new SourceCodeParamsYaml.FunctionDefForSourceCode("some-function:1.0");

        p.metas.addAll(
                List.of(Map.of("assembled-raw", "assembled-raw"),
                        Map.of("dataset", "dataset-processing"),
                        Map.of("feature", "feature"))
        );
        SourceCodeParamsYaml sourceCodeParamsYaml = new SourceCodeParamsYaml();
        SourceCode sourceCodeYaml = new SourceCode();
        sourceCodeYaml.uid = "test-process-for-meta";
        sourceCodeYaml.processes.add(p);
        sourceCodeParamsYaml.source = sourceCodeYaml;

        String s = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.toString(sourceCodeParamsYaml);
        SourceCodeParamsYaml planParams = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(s);
        SourceCode sourceCodeYamlV21 = planParams.source;

        SourceCodeParamsYaml.Process p1 = sourceCodeYamlV21.getProcesses().get(0);

        final Meta meta1 = MetaUtils.getMeta(p.metas, "assembled-raw");
        assertNotNull(meta1);
        assertEquals("assembled-raw", Objects.requireNonNull(meta1).getValue());

        final Meta dataset = MetaUtils.getMeta(p.metas, "dataset");
        assertNotNull(dataset);
        assertEquals("dataset-processing", Objects.requireNonNull(dataset).getValue());

        final Meta feature = MetaUtils.getMeta(p.metas, "feature");
        assertNotNull(feature);
        assertEquals("feature", Objects.requireNonNull(feature).getValue());
    }

}

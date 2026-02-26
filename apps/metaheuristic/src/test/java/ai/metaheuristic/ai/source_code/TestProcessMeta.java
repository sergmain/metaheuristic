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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.commons.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Execution(ExecutionMode.CONCURRENT)
public class TestProcessMeta {

    @Test
    public void testProcessMeta() {
        SourceCodeParamsYaml.SourceCode sourceCodeYaml = new SourceCodeParamsYaml.SourceCode();
        sourceCodeYaml.uid = "test-process-for-meta";
        {
            SourceCodeParamsYaml.Process p = new SourceCodeParamsYaml.Process();
            p.name = "experiment";
            p.code = "test-experiment-code-01";
            p.outputs.add(new SourceCodeParamsYaml.Variable("model"));
            p.function = new SourceCodeParamsYaml.FunctionDefForSourceCode(Consts.MH_FINISH_FUNCTION, EnumsApi.FunctionExecContext.internal);

            p.metas = List.of(
                    Map.of("assembled-raw", "assembled-raw"),
                    Map.of("dataset", "dataset-processing"),
                    Map.of("feature", "feature")
            );

            sourceCodeYaml.processes.add(p);
        }
        SourceCodeParamsYaml sourceCodeParamsYaml = new SourceCodeParamsYaml();
        sourceCodeParamsYaml.source = sourceCodeYaml;

        String s = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.toString(sourceCodeParamsYaml);

        System.out.println(s);

        SourceCodeParamsYaml planParams = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(s);
        SourceCodeParamsYaml.SourceCode yaml1 = planParams.source;

        assertEquals(sourceCodeYaml, yaml1);

    }

}

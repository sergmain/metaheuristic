/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.ai.yaml.exec_context;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYamlV3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 11/12/2019
 * Time: 3:33 PM
 */
public class TestExecContextParams {

    @Test
    public void testVersion() {
        assertEquals( new ExecContextParamsYaml().version, ExecContextParamsYamlUtils.BASE_YAML_UTILS.getDefault().getVersion() );
    }

    @Test
    public void testMarshaling() {
        ExecContextParamsYamlV3 expy = new ExecContextParamsYamlV3();

        ExecContextParamsYamlV3.FunctionDefinitionV3 fd1 = new ExecContextParamsYamlV3.FunctionDefinitionV3("function#1");
        ExecContextParamsYamlV3.ProcessV3 p1 = new ExecContextParamsYamlV3.ProcessV3("process #1", "process #1", Consts.TOP_LEVEL_CONTEXT_ID, fd1);

        ExecContextParamsYamlV3.FunctionDefinitionV3 fd2 = new ExecContextParamsYamlV3.FunctionDefinitionV3("function#2");
        ExecContextParamsYamlV3.ProcessV3 p2 = new ExecContextParamsYamlV3.ProcessV3("process #2", "process #2", Consts.TOP_LEVEL_CONTEXT_ID, fd2);

        expy.processes.add(p1);
        expy.processes.add(p2);
        String sv3 = ExecContextParamsYamlUtils.BASE_YAML_UTILS.toStringAsVersion(expy, 3);

        ExecContextParamsYaml expy1 = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(sv3);

        expy1.execContextGraph = new ExecContextParamsYaml.ExecContextGraph(13L, 42L, "aaa");

        ExecContextParamsYaml.Process p = expy1.findProcess("process#3");
        assertNull(p);

        String s = ExecContextParamsYamlUtils.BASE_YAML_UTILS.toString(expy1);

        System.out.println(s);
        assertFalse(s.contains("processMap"));

        ExecContextParamsYaml expy2 = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(s);

        assertNotNull(expy2.execContextGraph);
        assertEquals(13L, expy2.execContextGraph.rootExecContextId);
        assertEquals(42L, expy2.execContextGraph.parentExecContextId);
        assertEquals("aaa", expy2.execContextGraph.graph);

    }
}

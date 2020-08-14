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

package ai.metaheuristic.ai.yaml.exec_context;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
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
        ExecContextParamsYaml expy = new ExecContextParamsYaml();

        ExecContextParamsYaml.FunctionDefinition fd1 = new ExecContextParamsYaml.FunctionDefinition("function#1");
        ExecContextParamsYaml.Process p1 = new ExecContextParamsYaml.Process("process #1", "process #1", Consts.TOP_LEVEL_CONTEXT_ID, fd1);

        ExecContextParamsYaml.FunctionDefinition fd2 = new ExecContextParamsYaml.FunctionDefinition("function#2");
        ExecContextParamsYaml.Process p2 = new ExecContextParamsYaml.Process("process #2", "process #2", Consts.TOP_LEVEL_CONTEXT_ID, fd2);

        expy.processes.add(p1);
        expy.processes.add(p2);

        ExecContextParamsYaml.Process p = expy.findProcess("process#3");
        assertNull(p);

        String s = ExecContextParamsYamlUtils.BASE_YAML_UTILS.toString(expy);

        System.out.println(s);
        assertFalse(s.contains("processMap"));

    }
}

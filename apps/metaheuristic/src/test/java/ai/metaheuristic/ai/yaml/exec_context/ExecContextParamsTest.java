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

package ai.metaheuristic.ai.yaml.exec_context;

import ai.metaheuristic.api.data.exec_context.ExecContextParams;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYamlV3;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYamlV5;
import ai.metaheuristic.commons.CommonConsts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 11/12/2019
 * Time: 3:33 PM
 */
@Execution(ExecutionMode.CONCURRENT)
public class ExecContextParamsTest {

    @Test
    public void testVersion() {
        assertEquals( new ExecContextParams().version, ExecContextParamsUtils.BASE_UTILS.getDefault().getVersion() );
    }

    @Test
    public void testMarshaling() {
        ExecContextParamsYamlV3 expy = new ExecContextParamsYamlV3();

        ExecContextParamsYamlV3.FunctionDefinitionV3 fd1 = new ExecContextParamsYamlV3.FunctionDefinitionV3("function#1");
        ExecContextParamsYamlV3.ProcessV3 p1 = new ExecContextParamsYamlV3.ProcessV3("process #1", "process #1", CommonConsts.TOP_LEVEL_CONTEXT_ID, fd1);

        ExecContextParamsYamlV3.FunctionDefinitionV3 fd2 = new ExecContextParamsYamlV3.FunctionDefinitionV3("function#2");
        ExecContextParamsYamlV3.ProcessV3 p2 = new ExecContextParamsYamlV3.ProcessV3("process #2", "process #2", CommonConsts.TOP_LEVEL_CONTEXT_ID, fd2);

        expy.processes.add(p1);
        expy.processes.add(p2);
        String sv3 = ExecContextParamsUtils.BASE_UTILS.toStringAsVersion(expy, 3);

        ExecContextParams expy1 = ExecContextParamsUtils.BASE_UTILS.to(sv3);

        expy1.execContextGraph = new ExecContextParams.ExecContextGraph(13L, 42L, "aaa");

        ExecContextParams.Process p = expy1.findProcess("process#3");
        assertNull(p);

        String s = ExecContextParamsUtils.BASE_UTILS.toString(expy1);

        System.out.println(s);
        assertFalse(s.contains("processMap"));

        ExecContextParams expy2 = ExecContextParamsUtils.BASE_UTILS.to(s);

        assertNotNull(expy2.execContextGraph);
        assertEquals(13L, expy2.execContextGraph.rootExecContextId);
        assertEquals(42L, expy2.execContextGraph.parentExecContextId);
        assertEquals("aaa", expy2.execContextGraph.graph);

    }

    @Test
    public void testV5UpgradesToV6WithEmptyGroups() {
        // Phase 5 acceptance (a): a v5 EC upgrades through the v6 chain unchanged, gaining an empty groups list.
        ExecContextParamsYamlV5 v5 = new ExecContextParamsYamlV5();
        ExecContextParamsYamlV5.FunctionDefinitionV5 fd = new ExecContextParamsYamlV5.FunctionDefinitionV5("fn#1");
        ExecContextParamsYamlV5.ProcessV5 pr = new ExecContextParamsYamlV5.ProcessV5(
                "proc#1", "proc#1", CommonConsts.TOP_LEVEL_CONTEXT_ID, fd);
        v5.processes.add(pr);

        String sv5 = ExecContextParamsUtils.BASE_UTILS.toStringAsVersion(v5, 5);
        ExecContextParams latest = ExecContextParamsUtils.BASE_UTILS.to(sv5);

        assertEquals(6, latest.version);
        assertEquals(1, latest.processes.size());
        assertEquals("proc#1", latest.processes.get(0).processCode);
        assertNotNull(latest.groups);
        assertTrue(latest.groups.isEmpty());
    }

    @Test
    public void testV6GroupRoundTrips() {
        // Phase 5 acceptance (b, IR half): a v6 group entry serializes and deserializes intact.
        ExecContextParams py = new ExecContextParams();
        ExecContextParams.Group g = new ExecContextParams.Group("grp-A");
        g.body.add(new ExecContextParams.Process("b#1", "b#1", CommonConsts.TOP_LEVEL_CONTEXT_ID,
                new ExecContextParams.FunctionDefinition("fn#body")));
        g.inputs.add(new ExecContextParams.Variable("in1"));
        g.outputs.add(new ExecContextParams.Variable("out1"));
        g.internalContextId = "1,2";
        g.resetPointProcessCode = "b#1";
        py.groups.add(g);

        String s = ExecContextParamsUtils.BASE_UTILS.toString(py);
        ExecContextParams back = ExecContextParamsUtils.BASE_UTILS.to(s);

        assertEquals(1, back.groups.size());
        ExecContextParams.Group g2 = back.groups.get(0);
        assertEquals("grp-A", g2.name);
        assertEquals(1, g2.body.size());
        assertEquals("b#1", g2.body.get(0).processCode);
        assertEquals(1, g2.inputs.size());
        assertEquals("in1", g2.inputs.get(0).name);
        assertEquals(1, g2.outputs.size());
        assertEquals("out1", g2.outputs.get(0).name);
        assertEquals("1,2", g2.internalContextId);
        assertEquals("b#1", g2.resetPointProcessCode);
    }

    @Test
    public void testV6GraftNodeRoundTrips() {
        // Phase 6.3b (Option A): an in-band graft node on a Process survives a v6 YAML round-trip.
        ExecContextParams py = new ExecContextParams();
        ExecContextParams.Process p = new ExecContextParams.Process(
                "graft-node", "graft-node", "1,2", new ExecContextParams.FunctionDefinition("mh.nop"));
        ExecContextParams.Graft graft = new ExecContextParams.Graft("grp-A");
        graft.inputBindings.add("inA");
        graft.inputBindings.add("inB");
        graft.outputBindings.add("outC");
        graft.driver = "run-now";
        graft.at = "root";
        p.graft = graft;
        py.processes.add(p);

        String s = ExecContextParamsUtils.BASE_UTILS.toString(py);
        ExecContextParams back = ExecContextParamsUtils.BASE_UTILS.to(s);

        assertEquals(1, back.processes.size());
        ExecContextParams.Graft g2 = back.processes.get(0).graft;
        assertNotNull(g2);
        assertEquals("grp-A", g2.groupName);
        assertEquals(java.util.List.of("inA", "inB"), g2.inputBindings);
        assertEquals(java.util.List.of("outC"), g2.outputBindings);
        assertEquals("run-now", g2.driver);
        assertEquals("root", g2.at);
    }
}

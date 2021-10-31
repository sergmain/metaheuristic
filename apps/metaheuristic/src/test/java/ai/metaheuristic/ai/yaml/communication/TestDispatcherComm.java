/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.yaml.communication;

import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Serge
 * Date: 11/12/2019
 * Time: 3:16 PM
 */
public class TestDispatcherComm {

    @Test
    public void testVersion() {
        assertEquals( new DispatcherCommParamsYaml().version, DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.getDefault().getVersion() );
    }

    @Test
    public void testMarshalling() {
        DispatcherCommParamsYaml o = new DispatcherCommParamsYaml();
        DispatcherCommParamsYaml.DispatcherResponse resp = new DispatcherCommParamsYaml.DispatcherResponse();
        resp.processorCode = "proc-1";
        o.responses.add(resp);

        final DispatcherCommParamsYaml.AssignedTask at = new DispatcherCommParamsYaml.AssignedTask();
        resp.assignedTask = at;
        resp.assignedTask.taskId = 11L;
        resp.assignedTask.execContextId = 15L;
        resp.assignedTask.tag = "tag1";
        resp.assignedTask.quota = 99 ;
        resp.assignedTask.params = "params";
        resp.assignedTask.state = EnumsApi.ExecContextState.STARTED;

        String s = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.toString(o);
        DispatcherCommParamsYaml o1 = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(s);
        assertEquals(1, o1.responses.size());
        assertNotNull(o1.responses.get(0));
        assertEquals("proc-1", o1.responses.get(0).processorCode);


        DispatcherCommParamsYaml.AssignedTask at1 = o1.responses.get(0).assignedTask;
        assertNotNull(at1);

        assertEquals(at.taskId, at1.taskId);
        assertEquals(at.execContextId, at1.execContextId);
        assertEquals(at.tag, at1.tag);
        assertEquals(at.quota, at1.quota);
        assertEquals(at.params, at1.params);
        assertEquals(at.state, at1.state);
    }
}

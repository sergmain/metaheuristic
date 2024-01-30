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

package ai.metaheuristic.ai.dispatcher.exec_context_task_state;

import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYaml;
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYamlUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Sergio Lissner
 * Date: 9/27/2023
 * Time: 9:59 AM
 */
@Execution(CONCURRENT)
public class ExecContextTaskStateUtilsTest {

    @Test
    public void test_getFinishedTaskVertices() {

        String yaml = """
            states:
              45064: OK
              45065: NONE
              45066: OK
              45067: OK
              45068: OK
              45063: OK
            triesWasMade: {
              }
            version: 1
            """;

        ExecContextTaskStateParamsYaml paramsYaml = ExecContextTaskStateParamsYamlUtils.BASE_YAML_UTILS.to(yaml);

        List<Long> l = ExecContextTaskStateUtils.getFinishedTaskVertices(paramsYaml);
        assertEquals(5, l.size());
        assertTrue(l.contains(45064L));
        assertTrue(l.contains(45066L));
        assertTrue(l.contains(45067L));
        assertTrue(l.contains(45068L));
        assertTrue(l.contains(45063L));
        assertFalse(l.contains(45065L));

        l = ExecContextTaskStateUtils.getUnfinishedTaskVertices(paramsYaml);
        assertEquals(1, l.size());
        assertTrue(l.contains(45065L));
        assertFalse(l.contains(45064L));
        assertFalse(l.contains(45066L));
        assertFalse(l.contains(45067L));
        assertFalse(l.contains(45068L));
        assertFalse(l.contains(45063L));


    }
}

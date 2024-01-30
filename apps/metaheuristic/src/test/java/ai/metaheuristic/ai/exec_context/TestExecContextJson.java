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

package ai.metaheuristic.ai.exec_context;

import ai.metaheuristic.commons.utils.JsonUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 11/14/2020
 * Time: 7:07 PM
 */
public class TestExecContextJson {

    @SneakyThrows
    @Test
    public void test() {
        ExecContextApiData.VariableState info;
        info = new ExecContextApiData.VariableState(1L, 1111L, 2L, "1,2###1", "process-1", "function-1",
                List.of(new ExecContextApiData.VariableInfo(3L, "var-name-3", EnumsApi.VariableContext.local, null)),
                List.of(new ExecContextApiData.VariableInfo(4L, "var-name-4", EnumsApi.VariableContext.global, ".zip")) );

        String s = JsonUtils.getMapper().writeValueAsString(info);

        System.out.println(s);
        assertTrue(s.contains("ins"));
        assertTrue(s.contains("outs"));

        info = new ExecContextApiData.VariableState(1L, 1111L, 2L, "1,2###1", "process-1", "function-1",
                null, List.of() );

        s = JsonUtils.getMapper().writeValueAsString(info);

        System.out.println(s);
        assertFalse(s.contains("ins"));
        assertFalse(s.contains("outs"));
        assertEquals("{\"tId\":1,\"pId\":1111,\"ecId\":2,\"tCtxId\":\"1,2###1\",\"p\":\"process-1\",\"f\":\"function-1\"}", s);

    }
}

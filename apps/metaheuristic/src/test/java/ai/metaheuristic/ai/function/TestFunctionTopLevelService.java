/*
 * Metaheuristic, Copyright (C) 2017-2022, Innovation platforms, LLC
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

package ai.metaheuristic.ai.function;

import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.data.FunctionData;
import ai.metaheuristic.ai.dispatcher.function.FunctionTopLevelService;
import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Serge
 * Date: 4/24/2022
 * Time: 2:25 AM
 */
public class TestFunctionTopLevelService {

    @Test
    public void test_() {

        Map<Long, Function> map = Map.of(
                1L, new Function(1L, 0, "code-1", "type", "params"),
                3L, new Function(3L, 0, "code-3", "type", "params"),
                2L, new Function(2L, 0, "code-2", "type", "params")
        );

        FunctionData.FunctionsResult r = FunctionTopLevelService.getFunctions(()->List.of(2L, 1L, 3L), map::get, EnumsApi.DispatcherAssetMode.local);
        assertNotNull(r.functions);
        assertEquals(3, r.functions.size());

        assertEquals(3, r.functions.get(0).id);
        assertEquals(2, r.functions.get(1).id);
        assertEquals(1, r.functions.get(2).id);

    }

}

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

package ai.metaheuristic.ai.internal_function.aggregate;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static ai.metaheuristic.ai.dispatcher.internal_functions.aggregate.AggregateFunction.VARIABLES;
import static ai.metaheuristic.ai.dispatcher.internal_functions.aggregate.AggregateFunction.getNamesOfVariables;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Sergio Lissner
 * Date: 5/18/2023
 * Time: 1:22 AM
 */
public class AggregateFunctionTest {

    @Test
    public void test_() {
        assertNull(getNamesOfVariables(List.of(Map.of(VARIABLES, " "))));
        assertNull(getNamesOfVariables(List.of()));
        assertNull(getNamesOfVariables(List.of(Map.of(VARIABLES, ""))));

        assertEquals(List.of("aaa"), getNamesOfVariables(List.of(Map.of(VARIABLES, "aaa"))));
        assertEquals(List.of("aaa", "bbb"), getNamesOfVariables(List.of(Map.of(VARIABLES, "aaa,bbb"))));
        assertEquals(List.of("aaa", "bbb"), getNamesOfVariables(List.of(Map.of(VARIABLES, "aaa, bbb"))));
        assertEquals(List.of("aaa", "bbb"), getNamesOfVariables(List.of(Map.of(VARIABLES, "[[aaa]], [[bbb]]"))));
        assertEquals(List.of("aaa", "bbb"), getNamesOfVariables(List.of(Map.of(VARIABLES, "[[aaa]],[[bbb]]"))));
        assertEquals(List.of("aaa_bbb"), getNamesOfVariables(List.of(Map.of(VARIABLES, "aaa bbb"))));
        assertEquals(List.of("aaa_bbb"), getNamesOfVariables(List.of(Map.of(VARIABLES, "[[aaa bbb]]"))));



    }
}

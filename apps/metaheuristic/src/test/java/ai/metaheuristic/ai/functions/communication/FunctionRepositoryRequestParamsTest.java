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

package ai.metaheuristic.ai.functions.communication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Sergio Lissner
 * Date: 11/16/2023
 * Time: 1:19 PM
 */
@Execution(CONCURRENT)
public class FunctionRepositoryRequestParamsTest {

    @Test
    public void test_functionRepositoryRequestParamsUtils_nulls() {
        FunctionRepositoryRequestParams p = new FunctionRepositoryRequestParams();


        String s = FunctionRepositoryRequestParamsUtils.UTILS.toString(p);


        System.out.println(s);
        assertFalse(s.contains("processorId"));
        assertFalse(s.contains("functionCodes"));

        assertDoesNotThrow(()->FunctionRepositoryRequestParamsUtils.UTILS.to(s));
    }

    @Test
    public void test_functionRepositoryRequestParamsUtils() {
        FunctionRepositoryRequestParams p = new FunctionRepositoryRequestParams();
        p.processorId = 1L;
        p.functionCodes = List.of("a1", "b2");


        String s = FunctionRepositoryRequestParamsUtils.UTILS.toString(p);


        System.out.println(s);
        assertTrue(s.contains("processorId"));
        assertTrue(s.contains("functionCodes"));

        FunctionRepositoryRequestParams p1 = assertDoesNotThrow(()->FunctionRepositoryRequestParamsUtils.UTILS.to(s));

        assertEquals(1L, p1.processorId);
        assertNotNull(p1.functionCodes);
        assertTrue(p1.functionCodes.contains("a1"));
        assertTrue(p1.functionCodes.contains("b2"));
    }
}

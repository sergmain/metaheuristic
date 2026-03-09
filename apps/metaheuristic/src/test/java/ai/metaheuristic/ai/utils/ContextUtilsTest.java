/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sergio Lissner
 * Date: 3/8/2026
 * Time: 3:59 PM
 */
@Execution(ExecutionMode.CONCURRENT)
class ContextUtilsTest {

    @Test
    void test_filterTaskContexts_1() {
        String taskContextId1 = "1,2";
        List<String> ctxIds = List.of("1,2,5,6|1|0#0", "1,2,5|1#0", "1,2,5,6", "1", "1,2,5");

        // aсt
        var result = ContextUtils.filterTaskContexts(taskContextId1, ctxIds);


        assertEquals(4, result.size());
        assertFalse(result.contains("1"));

        assertTrue(result.contains("1,2,5,6|1|0#0"));
        assertTrue(result.contains("1,2,5|1#0"));
        assertTrue(result.contains("1,2,5,6"));
        assertTrue(result.contains("1,2,5"));
    }

    @Test
    void test_filterTaskContexts_2() {
        String taskContextId1 = "1,2,5,6|1|0#0";
        List<String> ctxIds = List.of("1,2,5,6|1|0#0", "1,2,5|1#0", "1,2,5,6", "1", "1,2,5");

        // aсt
        var result = ContextUtils.filterTaskContexts(taskContextId1, ctxIds);


        assertEquals(1, result.size());
        assertFalse(result.contains("1"));
        assertFalse(result.contains("1,2,5|1#0"));
        assertFalse(result.contains("1,2,5,6"));
        assertFalse(result.contains("1,2,5"));

        assertTrue(result.contains("1,2,5,6|1|0#0"));
    }
}
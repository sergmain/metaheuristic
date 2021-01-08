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

package ai.metaheuristic.ai.function;

import ai.metaheuristic.ai.dispatcher.function.FunctionTopLevelService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Serge
 * Date: 3/12/2020
 * Time: 12:41 AM
 */
public class TestProduceFinalCommandLineParams {

    @Test
    public void test() {
        assertEquals("", FunctionTopLevelService.produceFinalCommandLineParams(null, null));
        assertEquals("", FunctionTopLevelService.produceFinalCommandLineParams("", ""));
        assertEquals("123", FunctionTopLevelService.produceFinalCommandLineParams("123", null));
        assertEquals("123", FunctionTopLevelService.produceFinalCommandLineParams("123", ""));
        assertEquals("456", FunctionTopLevelService.produceFinalCommandLineParams(null, "456"));
        assertEquals("456", FunctionTopLevelService.produceFinalCommandLineParams("", "456"));
        assertEquals("123 456", FunctionTopLevelService.produceFinalCommandLineParams("123", "456"));
    }

}

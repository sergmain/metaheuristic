/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.function.FunctionService;
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
        assertEquals("", FunctionService.produceFinalCommandLineParams(null, null));
        assertEquals("", FunctionService.produceFinalCommandLineParams("", ""));
        assertEquals("123", FunctionService.produceFinalCommandLineParams("123", null));
        assertEquals("123", FunctionService.produceFinalCommandLineParams("123", ""));
        assertEquals("456", FunctionService.produceFinalCommandLineParams(null, "456"));
        assertEquals("456", FunctionService.produceFinalCommandLineParams("", "456"));
        assertEquals("123 456", FunctionService.produceFinalCommandLineParams("123", "456"));
    }

}

/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

import org.junit.jupiter.api.Test;

import static ai.metaheuristic.ai.dispatcher.function.FunctionService.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Serge
 * Date: 3/12/2020
 * Time: 12:41 AM
 */
public class TestProduceFinalCommandLineParams {

    @Test
    public void test() {
        assertEquals("", produceFinalCommandLineParams(null, null));
        assertEquals("", produceFinalCommandLineParams("", ""));
        assertEquals("123", produceFinalCommandLineParams("123", null));
        assertEquals("123", produceFinalCommandLineParams("123", ""));
        assertEquals("456", produceFinalCommandLineParams(null, "456"));
        assertEquals("456", produceFinalCommandLineParams("", "456"));
        assertEquals("123 456", produceFinalCommandLineParams("123", "456"));
    }

}

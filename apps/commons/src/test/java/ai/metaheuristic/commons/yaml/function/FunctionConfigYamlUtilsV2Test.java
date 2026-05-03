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

package ai.metaheuristic.commons.yaml.function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Sergio Lissner
 * Date: 4/26/2026
 * Time: 10:58 PM
 */
@Execution(CONCURRENT)
class FunctionConfigYamlUtilsV2Test {

    @Test
    void test_to_1() {
        FunctionConfigYamlV2.FunctionConfigV2 src = new FunctionConfigYamlV2.FunctionConfigV2();
        src.api = new FunctionConfigYamlV2.ApiV2("123");


        // aсt
        var result = FunctionConfigYamlUtilsV2.to(src);


        assertNotNull(result.api);
        assertEquals("123", result.api.keyCode);

    }

}
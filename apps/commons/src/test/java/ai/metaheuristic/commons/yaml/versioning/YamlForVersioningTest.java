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

package ai.metaheuristic.commons.yaml.versioning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Sergio Lissner
 * Date: 2/24/2026
 * Time: 7:05 PM
 */
@Execution(CONCURRENT)
class YamlForVersioningTest {

    @Test
    void test_getParamsVersion_1() {
        String yaml = """
            version: 1
            """;


        // aсt
        var result = YamlForVersioning.getParamsVersion(yaml);


        assertNotNull(result);
        assertEquals(1, result.version);
    }

    @Test
    void test_getParamsVersion_2() {
        String yaml = """
            version: 4
            source:
              instances: 1
              strictNaming: true
            """;


        // aсt
        var result = YamlForVersioning.getParamsVersion(yaml);


        assertNotNull(result);
        assertEquals(4, result.version);
    }
@Test
    void test_getParamsVersion_3() {
        String yaml = """
            source:
              instances: 1
              strictNaming: true
            """;


        // aсt
        var result = YamlForVersioning.getParamsVersion(yaml);


        assertNotNull(result);
        assertNull(result.version);
        assertEquals(1, result.getActualVersion());
    }
}
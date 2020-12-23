/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

package ai.metaheuristic.ai.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * User: Serg
 * Date: 24.07.2017
 * Time: 22:09
 */
public class TestRegexSplit {
    @Test
    public void testSplit() {
        assertArrayEquals(new String[]{"str1", "str2", "\"str 3\"", "str4"}, Arrays.stream("str1, str2, \"str 3\",  str4".split("[,]")).filter(s -> s != null && s.length() > 0).map(String :: trim).toArray());
        assertEquals(Arrays.asList("str1", "str2", "\"str 3\"", "str4"), Arrays.stream("str1, str2, \"str 3\",  str4".split("[,]")).filter(s -> s != null && s.length() > 0).map(String :: trim).collect(Collectors.toList()));
    }
}

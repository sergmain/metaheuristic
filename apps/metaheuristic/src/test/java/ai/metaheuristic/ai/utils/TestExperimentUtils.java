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

package ai.metaheuristic.ai.utils;

import ai.metaheuristic.ai.dispatcher.variable.InlineVariableUtils;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class TestExperimentUtils {

    @Test
    public void testNumberOfVariants() {

        final String listAsStr = String.valueOf(Arrays.asList("aaa","bbb","ccc"));
        InlineVariableUtils.NumberOfVariants nov = InlineVariableUtils.getNumberOfVariants(listAsStr);

        assertNotNull(nov);
        assertTrue(nov.status);
        assertNotNull(nov.values);
        assertNull(nov.error);

        assertEquals(3, nov.getCount());
        assertEquals(3, nov.values.size());

        assertEquals("aaa", nov.values.get(0));
        assertEquals("bbb", nov.values.get(1));
        assertEquals("ccc", nov.values.get(2));
    }
}

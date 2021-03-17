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

package ai.metaheuristic.ai.utils;

import org.junit.jupiter.api.Test;

import static ai.metaheuristic.ai.utils.ContextUtils.CONTEXT_SEPARATOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Serge
 * Date: 8/22/2020
 * Time: 6:33 PM
 */
public class TestContextUtils {

    @Test
    public void testCreateContext() {

    }

    @Test
    public void testGetWithoutSubContext() {
        assertEquals("123", ContextUtils.getWithoutSubContext("123"));
        assertEquals("123", ContextUtils.getWithoutSubContext("123"+CONTEXT_SEPARATOR+"1"));
    }

    @Test
    public void testGetSubContext() {
        assertNull(ContextUtils.getSubContext(""));
        assertNull(ContextUtils.getSubContext("123"));
        assertEquals("1", ContextUtils.getSubContext("123"+CONTEXT_SEPARATOR+"1"));
        assertEquals("2", ContextUtils.getSubContext("123"+CONTEXT_SEPARATOR+"2"));
    }


}

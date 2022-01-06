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

import java.util.Collection;
import java.util.List;
import java.util.Set;

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

    @Test
    public void testContextIdComparator_11() {
        Set<String> set = Set.of("1,2,3,4", "1,2#1", "1,2",  "1", "1,2,3,4,5#1");
        check(set);
    }

    @Test
    public void testContextIdComparator_12() {
        List<String> set = List.of("1,2,3,4", "1,2#1", "1,2", "1", "1,2,3,4,5#1");
        check(set);
    }

    private static void check(Collection<String> collection) {
        List<String> list = ContextUtils.sortSetAsTaskContextId(collection);

        assertEquals("1,2,3,4,5#1", list.get(0), list.toString());
        assertEquals("1,2,3,4", list.get(1), list.toString());
        assertEquals("1,2#1", list.get(2), list.toString());
        assertEquals("1,2", list.get(3), list.toString());
        assertEquals("1", list.get(4), list.toString());
    }

    @Test
    public void testContextIdComparator_1() {
        List<String> set = List.of("1,4#1", "1", "1,2", "1,3");
        List<String> list = ContextUtils.sortSetAsTaskContextId(set);

        assertEquals("1,4#1", list.get(0), list.toString());
        assertEquals("1,3", list.get(1), list.toString());
        assertEquals("1,2", list.get(2), list.toString());
        assertEquals("1", list.get(3), list.toString());

    }

    @Test
    public void testContextIdComparator_2() {
        List<String> set = List.of("1,4#1", "1", "1,2#2", "1,2#1");
        List<String> list = ContextUtils.sortSetAsTaskContextId(set);

        assertEquals("1,4#1", list.get(0), list.toString());
        assertEquals("1,2#2", list.get(1), list.toString());
        assertEquals("1,2#1", list.get(2), list.toString());
        assertEquals("1", list.get(3), list.toString());

    }

}

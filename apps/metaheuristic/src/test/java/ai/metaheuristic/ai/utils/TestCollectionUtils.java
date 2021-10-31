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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 11/13/2020
 * Time: 4:22 PM
 */
public class TestCollectionUtils {

    @Test
    public void test() {
        assertTrue(CollectionUtils.checkTagAllowed(null, null));
        assertTrue(CollectionUtils.checkTagAllowed(null, " "));
        assertTrue(CollectionUtils.checkTagAllowed(" ", null));
        assertTrue(CollectionUtils.checkTagAllowed(" ", " "));

        assertFalse(CollectionUtils.checkTagAllowed("aaa", null));
        assertFalse(CollectionUtils.checkTagAllowed("aaa", " "));

        assertFalse(CollectionUtils.checkTagAllowed("aaa, bbb", null));
        assertFalse(CollectionUtils.checkTagAllowed("aaa, bbb", " "));

        assertTrue(CollectionUtils.checkTagAllowed("", "aaa"));
        assertTrue(CollectionUtils.checkTagAllowed(null, "aaa"));

        assertTrue(CollectionUtils.checkTagAllowed(" aaa ", " aaa, ccc "));
        assertTrue(CollectionUtils.checkTagAllowed("aaa", " aaa, ccc "));
        assertTrue(CollectionUtils.checkTagAllowed("aaa", "aaa"));
        assertTrue(CollectionUtils.checkTagAllowed("aaa", "aaa,"));
        assertTrue(CollectionUtils.checkTagAllowed("aaa", " aaa, "));

        assertFalse(CollectionUtils.checkTagAllowed("aaa, bbb", "aaa, ccc"));
        assertFalse(CollectionUtils.checkTagAllowed(" aaa, bbb ", " aaa, ccc "));

        assertFalse(CollectionUtils.checkTagAllowed("ddd, bbb", "aaa, ccc"));
        assertFalse(CollectionUtils.checkTagAllowed(" ddd, bbb ", " aaa, ccc "));
        assertFalse(CollectionUtils.checkTagAllowed(" ddd ", " aaa, ccc "));
        assertFalse(CollectionUtils.checkTagAllowed("ddd", " aaa, ccc "));
        assertFalse(CollectionUtils.checkTagAllowed("ddd", "aaa"));
        assertFalse(CollectionUtils.checkTagAllowed("ddd", "aaa,"));
        assertFalse(CollectionUtils.checkTagAllowed("ddd", " aaa, "));
    }

    @Test
    public void testListEquals() {
        assertTrue(CollectionUtils.isEquals(null, null));
        assertTrue(CollectionUtils.isEquals(null, List.of()));
        assertTrue(CollectionUtils.isEquals(List.of(), null));

        assertFalse(CollectionUtils.isEquals(null, List.of("aaa")));
        assertFalse(CollectionUtils.isEquals(List.of("aaa"), null));

        assertFalse(CollectionUtils.isEquals(List.of(), List.of("aaa")));
        assertFalse(CollectionUtils.isEquals(List.of("aaa"), List.of()));

        assertTrue(CollectionUtils.isEquals(List.of("aaa"), List.of("aaa")));
        assertTrue(CollectionUtils.isEquals(List.of("aaa"), List.of("aaa")));

        assertFalse(CollectionUtils.isEquals(List.of("aaa"), List.of("aaa", "bbb")));
        assertFalse(CollectionUtils.isEquals(List.of("aaa"), List.of("aaa", "bbb")));

        assertTrue(CollectionUtils.isEquals(List.of("aaa", "bbb"), List.of("aaa", "bbb")));
        assertTrue(CollectionUtils.isEquals(List.of("aaa", "bbb"), List.of("aaa", "bbb")));

        assertTrue(CollectionUtils.isEquals(List.of("bbb", "aaa"), List.of("aaa", "bbb")));
        assertTrue(CollectionUtils.isEquals(List.of("bbb", "aaa"), List.of("aaa", "bbb")));
    }

    public static final List<String> list = List.of("1", "2", "3", "4", "5");

    @Test
    public void testPaging() {
        List<List<String>> pages;

        pages = CollectionUtils.parseAsPages(list, 0);
        assertEquals(0, pages.size());

        pages = CollectionUtils.parseAsPages(List.of(), 0);
        assertEquals(0, pages.size());

        pages = CollectionUtils.parseAsPages(list, 1);
        assertEquals(5, pages.size());
        assertEquals(List.of("1"), pages.get(0));
        assertEquals(List.of("2"), pages.get(1));
        assertEquals(List.of("3"), pages.get(2));
        assertEquals(List.of("4"), pages.get(3));
        assertEquals(List.of("5"), pages.get(4));

        pages = CollectionUtils.parseAsPages(list, 4);
        assertEquals(2, pages.size());
        assertEquals(List.of("1", "2", "3", "4"), pages.get(0));
        assertEquals(List.of("5"), pages.get(1));

        pages = CollectionUtils.parseAsPages(list, 6);
        assertEquals(1, pages.size());
        assertEquals(List.of("1", "2", "3", "4", "5"), pages.get(0));
    }

}

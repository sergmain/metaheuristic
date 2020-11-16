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

package ai.metaheuristic.ai.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertTrue(CollectionUtils.checkTagAllowed("aaa, bbb", "aaa, ccc"));
        assertTrue(CollectionUtils.checkTagAllowed(" aaa, bbb ", " aaa, ccc "));
        assertTrue(CollectionUtils.checkTagAllowed(" aaa ", " aaa, ccc "));
        assertTrue(CollectionUtils.checkTagAllowed("aaa", " aaa, ccc "));
        assertTrue(CollectionUtils.checkTagAllowed("aaa", "aaa"));
        assertTrue(CollectionUtils.checkTagAllowed("aaa", "aaa,"));
        assertTrue(CollectionUtils.checkTagAllowed("aaa", " aaa, "));

        assertFalse(CollectionUtils.checkTagAllowed("ddd, bbb", "aaa, ccc"));
        assertFalse(CollectionUtils.checkTagAllowed(" ddd, bbb ", " aaa, ccc "));
        assertFalse(CollectionUtils.checkTagAllowed(" ddd ", " aaa, ccc "));
        assertFalse(CollectionUtils.checkTagAllowed("ddd", " aaa, ccc "));
        assertFalse(CollectionUtils.checkTagAllowed("ddd", "aaa"));
        assertFalse(CollectionUtils.checkTagAllowed("ddd", "aaa,"));
        assertFalse(CollectionUtils.checkTagAllowed("ddd", " aaa, "));


    }
}

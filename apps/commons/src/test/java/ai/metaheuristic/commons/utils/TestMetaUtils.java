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

package ai.metaheuristic.commons.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 3/3/2022
 * Time: 7:01 PM
 */
public class TestMetaUtils {

    @Test
    public void testRemove() {
        List<Map<String, String>> metas;

        metas = MetaUtils.remove(
                List.of(Map.of("aaa","321"),
                        Map.of("bbb", "123")), "aaa");
        assertNotNull(metas);
        assertEquals(1, metas.size());
        assertEquals("123", metas.get(0).get("bbb"));


        assertNull(MetaUtils.remove(null));
        assertNull(MetaUtils.remove(null, "aaa"));

        metas = MetaUtils.remove(List.of(), "aaa");
        assertNotNull(metas);
        assertTrue(metas.isEmpty());

        metas = MetaUtils.remove(List.of(Map.of("bbb", "123")));
        assertNotNull(metas);
        assertEquals(1, metas.size());
        assertEquals("123", metas.get(0).get("bbb"));

        metas = MetaUtils.remove(List.of(Map.of("bbb", "123")), "aaa");
        assertNotNull(metas);
        assertEquals(1, metas.size());
        assertEquals("123", metas.get(0).get("bbb"));

        metas = MetaUtils.remove(List.of(Map.of("aaa", "123")), "aaa");
        assertNotNull(metas);
        assertTrue(metas.isEmpty());

    }
}

/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.commons.utils.SnippetCoreUtils;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Serge
 * Date: 9/4/2019
 * Time: 8:29 PM
 */
public class TestSnippetUtils {


    @Test
    public void testMetas() {
        final SnippetApiData.SnippetConfig snippet = new SnippetApiData.SnippetConfig();
        snippet.metas = new ArrayList<>();
        snippet.metas.add(new Meta("key1", "value1", null));

        Meta m;
        m = SnippetCoreUtils.getMeta(snippet.metas, "key1");
        assertEquals("value1", m.getValue());

        m = SnippetCoreUtils.getMeta(snippet.metas, "key2", "key1");
        assertEquals("value1", m.getValue());

        m = SnippetCoreUtils.getMeta(snippet.metas, "key2", "key3");
        assertNull(m);

        m = SnippetCoreUtils.getMeta(snippet.metas);
        assertNull(m);

        snippet.metas = null;

        m = SnippetCoreUtils.getMeta(snippet.metas);
        assertNull(m);
    }

}

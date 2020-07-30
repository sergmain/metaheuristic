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
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 9/4/2019
 * Time: 8:29 PM
 */
public class TestFunctionUtils {


    @Test
    public void testMetas() {
        final FunctionConfigYaml function = new FunctionConfigYaml();
        Objects.requireNonNull(function.metas).add(Map.of("key1", "value1"));

        Meta m;
        m = MetaUtils.getMeta(function.metas, "key1");
        assertNotNull(m);
        assertEquals("value1", m.getValue());

        m = MetaUtils.getMeta(function.metas, "key2", "key1");
        assertNotNull(m);
        assertEquals("value1", m.getValue());

        m = MetaUtils.getMeta(function.metas, "key2", "key3");
        assertNull(m);

        m = MetaUtils.getMeta(function.metas);
        assertNull(m);

        function.metas.clear();

        m = MetaUtils.getMeta(function.metas);
        assertNull(m);
    }

}

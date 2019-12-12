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

package ai.metaheuristic.api.data;

import org.junit.Test;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Serge
 * Date: 12/12/2019
 * Time: 12:31 AM
 */
public class TestMetaBean {

    @Test
    public void test() throws NoSuchFieldException {
        Field[] fields = Meta.class.getDeclaredFields();
        assertEquals(3, fields.length);
        Field keyF = Meta.class.getDeclaredField("key");
        assertNotNull(keyF);
        assertEquals(String.class, keyF.getType());

        Field valueF = Meta.class.getDeclaredField("value");
        assertNotNull(valueF);
        assertEquals(String.class, valueF.getType());

        Field extF = Meta.class.getDeclaredField("ext");
        assertNotNull(extF);
        assertEquals(String.class, extF.getType());
    }
}

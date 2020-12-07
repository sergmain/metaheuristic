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

package ai.metaheuristic.ai.cache;

import ai.metaheuristic.ai.dispatcher.data.CacheData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Serge
 * Date: 10/24/2020
 * Time: 11:32 AM
 */
public class TestCacheKey {

    private static final CacheData.Key KEY = new CacheData.Key("function-01", "");
    static {
        KEY.inline.put("top-inline",Map.of("key-1","value-1"));
        KEY.inputs.addAll(List.of(
                new CacheData.Sha256PlusLength("sha256-1",42L),
                new CacheData.Sha256PlusLength("sha256-2",11L)));
    }

    private static final String KEY_AS_STRING = "{\"functionCode\":\"function-01\",\"funcParams\":\"\",\"inline\":{\"top-inline\":{\"key-1\":\"value-1\"}},\"inputs\":[{\"sha256\":\"sha256-1\",\"length\":42},{\"sha256\":\"sha256-2\",\"length\":11}]}";

    @Test
    public void test() {
        assertEquals(KEY_AS_STRING, KEY.asString());
    }

    public static void main(String[] args) {
        System.out.println(KEY.asString());
    }
}

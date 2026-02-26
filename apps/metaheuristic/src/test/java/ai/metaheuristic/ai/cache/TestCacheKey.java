/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * @author Serge
 * Date: 10/24/2020
 * Time: 11:32 AM
 */
@Execution(ExecutionMode.CONCURRENT)
public class TestCacheKey {

    @Test
    public void test() {
        final CacheData.FullKey KEY = new CacheData.FullKey("function-01", "");
        KEY.inline.put("top-inline", Map.of("key-1","value-1"));
        KEY.inputs.addAll(List.of(
                new CacheData.Sha256PlusLength("sha256-1",42L),
                new CacheData.Sha256PlusLength("sha256-2",11L)));

        System.out.println(KEY.asString());

        final String KEY_AS_STRING = "{\"functionCode\":\"function-01\",\"funcParams\":\"\",\"inline\":{\"top-inline\":{\"key-1\":\"value-1\"}},\"inputs\":[{\"sha256\":\"sha256-1\",\"length\":42},{\"sha256\":\"sha256-2\",\"length\":11}],\"metas\":[]}";


        assertEquals(KEY_AS_STRING, KEY.asString());
    }

    @Test
    public void test_1() {
        final CacheData.FullKey key1 = new CacheData.FullKey("function-01", "");
        key1.inline.put("top-inline",Map.of("key-1","value-1"));
        key1.inputs.addAll(List.of(
                new CacheData.Sha256PlusLength("sha256-1",42L),
                new CacheData.Sha256PlusLength("sha256-2",11L)));

        final CacheData.FullKey key2 = new CacheData.FullKey("function-01", "");
        key2.inline.put("top-inline",Map.of("key-1","value-2"));
        key2.inputs.addAll(List.of(
                new CacheData.Sha256PlusLength("sha256-1",42L),
                new CacheData.Sha256PlusLength("sha256-2",11L)));

        assertNotEquals(key1.asString(), key2.asString());
    }

    @Test
    public void test_2() {
        final CacheData.FullKey key1 = new CacheData.FullKey("function-01", "");
        key1.inline.put("top-inline",Map.of("key-2","value-2", "key-1","value-1"));
        key1.inputs.addAll(List.of(
                new CacheData.Sha256PlusLength("sha256-1",42L),
                new CacheData.Sha256PlusLength("sha256-2",11L)));

        final CacheData.FullKey key2 = new CacheData.FullKey("function-01", "");
        key2.inline.put("top-inline",Map.of("key-1","value-1", "key-2","value-2"));
        key2.inputs.addAll(List.of(
                new CacheData.Sha256PlusLength("sha256-1",42L),
                new CacheData.Sha256PlusLength("sha256-2",11L)));

        assertEquals(key1.asString(), key2.asString());
    }
}

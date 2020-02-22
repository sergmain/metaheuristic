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

package ai.metaheuristic.ai.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Serge
 * Date: 6/7/2019
 * Time: 4:57 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
public class CacheForTest {

    public static Map<Integer, SimpleBeanForTest> map = new HashMap<>();

    public static boolean cacheWasMissed;

    @CacheEvict(value = "testCache", key = "#result.id")
    public SimpleBeanForTest save(SimpleBeanForTest value) {
        map.put(value.id, value);
        return value;
    }

    @Cacheable(cacheNames = "testCache", unless="#result==null")
    public SimpleBeanForTest findById(Integer id) {
        cacheWasMissed = true;
        return map.get(id);
    }

    @CacheEvict(cacheNames = {"testCache"}, key = "#bean.id")
    public void delete(SimpleBeanForTest bean) {
        if (bean==null) {
            return;
        }
        map.remove(bean.id);
    }

    @CacheEvict(cacheNames = {"testCache"}, key = "#id")
    public void deleteById(Integer id) {
        if (id==null) {
            return;
        }
        map.remove(id);
    }

    @CacheEvict(cacheNames = {"testCache"}, key = "#id")
    public void evictById(Integer id) {
        //
    }

}

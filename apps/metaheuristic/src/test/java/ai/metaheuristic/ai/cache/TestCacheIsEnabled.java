/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.test.cache.CacheForTest;
import ai.metaheuristic.ai.dispatcher.test.cache.SimpleBeanForTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author Serge
 * Date: 6/7/2019
 * Time: 4:55 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
@DirtiesContext
public class TestCacheIsEnabled {

    @Autowired
    private CacheForTest cache;

    @Test
    public void test() {

        CacheForTest.cacheWasMissed = false;
        final SimpleBeanForTest bean1 = new SimpleBeanForTest(1, "aaa");
        cache.save(bean1);

        assertFalse(CacheForTest.cacheWasMissed);
        SimpleBeanForTest bean = cache.findById(1);

        assertEquals(bean1, bean);

        assertFalse(CacheForTest.cacheWasMissed);

        CacheForTest.cacheWasMissed = false;

        bean = cache.findById(1);
        assertEquals(bean1, bean);
        assertFalse(CacheForTest.cacheWasMissed);

    }
}

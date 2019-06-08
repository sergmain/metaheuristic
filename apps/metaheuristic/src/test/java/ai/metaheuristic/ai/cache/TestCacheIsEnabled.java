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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * @author Serge
 * Date: 6/7/2019
 * Time: 4:55 PM
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
public class TestCacheIsEnabled {

    @Autowired
    private CacheForTest cache;

    @Test
    public void test() {

        CacheForTest.cacheWasMissed = false;
        cache.save( new SimpleBeanForTest(1, "aaa"));

        assertFalse(CacheForTest.cacheWasMissed);
        SimpleBeanForTest bean = cache.findById(1);

        assertTrue(CacheForTest.cacheWasMissed);

        CacheForTest.cacheWasMissed = false;

        bean = cache.findById(1);
        assertFalse(CacheForTest.cacheWasMissed);

    }
}

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

package ai.metaheuristic.ai.utils;

import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Sergio Lissner
 * Date: 9/26/2023
 * Time: 6:34 AM
 */
public class HttpUtilsTest {

    @Test
    public void test_getHttpHost() throws URISyntaxException {

        HttpHost h = HttpUtils.getHttpHost("http://localhost:8889");
        assertEquals("localhost", h.getHostName());
        assertEquals(8889, h.getPort());
        assertEquals("http", h.getSchemeName());

        h = HttpUtils.getHttpHost("https://127.0.0.1:8888");
        assertEquals("127.0.0.1", h.getHostName());
        assertEquals(8888, h.getPort());
        assertEquals("https", h.getSchemeName());
    }
}

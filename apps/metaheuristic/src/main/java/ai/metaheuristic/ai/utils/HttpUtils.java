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

package ai.metaheuristic.ai.utils;

import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.http.HttpHost;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author Sergio Lissner
 * Date: 9/26/2023
 * Time: 6:33 AM
 */
public class HttpUtils {

    public static HttpHost getHttpHost(String url) throws URISyntaxException {
        return URIUtils.extractHost(new URI(url));
    }
}

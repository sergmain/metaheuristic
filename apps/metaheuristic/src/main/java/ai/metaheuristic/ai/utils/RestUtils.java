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

package ai.metaheuristic.ai.utils;

import org.apache.http.client.fluent.Request;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

public class RestUtils {

    public static void putNoCacheHeaders(Map<String, String> map) {
        map.put("cache-control", "no-cache");
        map.put("expires", "Tue, 01 Jan 1980 1:00:00 GMT");
        map.put("pragma", "no-cache");
    }

    public static void addHeaders(Request request) {
        Map<String, String> map = new HashMap<>();
        putNoCacheHeaders(map);
        for (Map.Entry<String, String> entry : map.entrySet()) {
            request.addHeader(entry.getKey(), entry.getValue());
        }
    }

    public static HttpHeaders getHeader(long length) {
        return getHeader(null, length);
    }

    public static HttpHeaders getHeader(@Nullable HttpHeaders httpHeaders, long length) {
        HttpHeaders header = httpHeaders != null ? httpHeaders : new HttpHeaders();
        header.setContentLength(length);
        header.setCacheControl("max-age=0");
        header.setExpires(0);
        header.setPragma("no-cache");

        return header;
    }
}

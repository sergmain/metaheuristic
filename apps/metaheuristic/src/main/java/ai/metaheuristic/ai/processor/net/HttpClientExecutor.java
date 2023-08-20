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

package ai.metaheuristic.ai.processor.net;

import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.client5.http.fluent.Executor;

import java.net.URL;

public class HttpClientExecutor {

    public static Executor getExecutor(String serverUrl, String restUsername, String restPassword) {
        HttpHost dispatcherHttpHostWithAuth;
        try {
            dispatcherHttpHostWithAuth = URIUtils.extractHost(new URL(serverUrl).toURI());
        } catch (Throwable th) {
            throw new IllegalArgumentException("Can't build HttpHost for "+serverUrl, th);
        }
        return Executor.newInstance()
                .authPreemptive(dispatcherHttpHostWithAuth)
                .auth(dispatcherHttpHostWithAuth,restUsername, restPassword.toCharArray());
    }
}

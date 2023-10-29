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

import ai.metaheuristic.ai.utils.HttpUtils;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.client5.http.fluent.Executor;
import org.springframework.lang.Nullable;

public class HttpClientExecutor {

    public static Executor getExecutor(String serverUrl) {
        return getExecutor(serverUrl, null, null);
    }

    public static Executor getExecutor(String serverUrl, @Nullable String restUsername, @Nullable String restPassword) {
        HttpHost dispatcherHttpHostWithAuth;
        try {
            dispatcherHttpHostWithAuth = HttpUtils.getHttpHost(serverUrl);
        } catch (Throwable th) {
            throw new IllegalArgumentException("Can't build HttpHost for "+serverUrl, th);
        }
        final Executor executor = Executor.newInstance().authPreemptive(dispatcherHttpHostWithAuth);
        if (restUsername!=null) {
            if (restPassword==null) {
                throw new IllegalArgumentException("restPassword must be not null");
            }
            executor.auth(dispatcherHttpHostWithAuth,restUsername, restPassword.toCharArray());
        }
        return executor;
    }
}

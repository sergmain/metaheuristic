/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.station.net;

import org.apache.http.HttpHost;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.utils.URIUtils;

import java.net.URL;

public class HttpClientExecutor {

    public static Executor getExecutor(String launchpadUrl, String restUsername, String restToken, String restPassword) {
        HttpHost launchpadHttpHostWithAuth;
        try {
            launchpadHttpHostWithAuth = URIUtils.extractHost(new URL(launchpadUrl).toURI());
        } catch (Throwable th) {
            throw new IllegalArgumentException("Can't build HttpHost for "+launchpadUrl, th);
        }
        return Executor.newInstance()
                .authPreemptive(launchpadHttpHostWithAuth)
                .auth(launchpadHttpHostWithAuth,restUsername+'=' + restToken, restPassword);
    }
}

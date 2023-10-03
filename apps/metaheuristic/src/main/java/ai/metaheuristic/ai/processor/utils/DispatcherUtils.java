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

package ai.metaheuristic.ai.processor.utils;

import ai.metaheuristic.ai.Consts;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import static org.apache.hc.client5.http.config.RequestConfig.custom;

/**
 * @author Serge
 * Date: 11/21/2020
 * Time: 2:23 AM
 */
public class DispatcherUtils {

    public static HttpComponentsClientHttpRequestFactory getHttpRequestFactory() {
        // https://github.com/spring-projects/spring-boot/issues/11379
        // https://issues.apache.org/jira/browse/HTTPCLIENT-1892
        // https://github.com/spring-projects/spring-framework/issues/21238

        SocketConfig socketConfig = SocketConfig.custom().setSoTimeout(Timeout.ofSeconds(10)).build();

        // https://github.com/apache/httpcomponents-client/blob/5.2.x/httpclient5/src/test/java/org/apache/hc/client5/http/examples/ClientConfiguration.java
        PoolingHttpClientConnectionManager manager = PoolingHttpClientConnectionManagerBuilder
            .create()
            .setDefaultSocketConfig(socketConfig)
            .setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(5))
                .setSocketTimeout(Timeout.ofSeconds(5))
                .setValidateAfterInactivity(TimeValue.ofSeconds(10))
                .setTimeToLive(TimeValue.ofHours(1))
                .build())
            .build();

        final HttpClient httpClient = HttpClientBuilder.create()
                .setConnectionManager(manager)
                .useSystemProperties()
//                .setDefaultRequestConfig(custom().setConnectTimeout(Timeout.ofSeconds(5)).build())
                .build();

        final HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectTimeout((int) Consts.DISPATCHER_SOCKET_TIMEOUT_MILLISECONDS);

        return requestFactory;
    }

}

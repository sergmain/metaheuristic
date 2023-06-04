/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import org.apache.hc.core5.http.client.HttpClient;
import org.apache.hc.core5.http.config.SocketConfig;
import org.apache.hc.core5.http.impl.client.HttpClientBuilder;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import static org.apache.http.client.config.RequestConfig.custom;

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

        SocketConfig socketConfig = SocketConfig.custom().setSoTimeout((int) Consts.DISPATCHER_SOCKET_TIMEOUT_MILLISECONDS).build();

        final HttpClientBuilder clientBuilder = HttpClientBuilder.create();
        clientBuilder.setDefaultSocketConfig(socketConfig);
        clientBuilder.useSystemProperties();
        clientBuilder.setDefaultRequestConfig(custom().setConnectTimeout((int) Consts.DISPATCHER_SOCKET_TIMEOUT_MILLISECONDS)
                .setSocketTimeout((int) Consts.DISPATCHER_SOCKET_TIMEOUT_MILLISECONDS)
                .build());
        final HttpClient httpClient = clientBuilder.build();

        final HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectTimeout((int) Consts.DISPATCHER_SOCKET_TIMEOUT_MILLISECONDS);
        requestFactory.setReadTimeout((int) Consts.DISPATCHER_SOCKET_TIMEOUT_MILLISECONDS);
        return requestFactory;
    }

}

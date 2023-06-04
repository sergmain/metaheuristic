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

package ai.metaheuristic.ai.dispatcher.replication;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.data.ReplicationData;
import ai.metaheuristic.ai.utils.JsonUtils;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.fluent.Content;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.client5.http.fluent.Executor;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

/**
 * @author Serge
 * Date: 1/13/2020
 * Time: 7:13 PM
 */
@SuppressWarnings("rawtypes")
@Service
@RequiredArgsConstructor
@Slf4j
@Profile("dispatcher")
public class ReplicationCoreService {

    public final Globals globals;

    public ReplicationData.AssetStateResponse getAssetStates() {
        ReplicationData.ReplicationAsset data = getData(
                "/rest/v1/replication/current-assets", ReplicationData.AssetStateResponse.class, null,
                (uri) -> Request.get(uri).connectTimeout(Timeout.ofSeconds(5))
                //.socketTimeout(20000)
        );
        if (data instanceof ReplicationData.AssetAcquiringError) {
            return new ReplicationData.AssetStateResponse(((ReplicationData.AssetAcquiringError) data).getErrorMessagesAsList());
        }
        ReplicationData.AssetStateResponse response = (ReplicationData.AssetStateResponse) data;
        return response;
    }

    private static Executor getExecutor(String dispatcherUrl, String restUsername, String restPassword) {
        HttpHost dispatcherHttpHostWithAuth;
        try {
            dispatcherHttpHostWithAuth = URIUtils.extractHost(new URL(dispatcherUrl).toURI());
        } catch (Throwable th) {
            throw new IllegalArgumentException("Can't build HttpHost for " + dispatcherUrl, th);
        }
        return Executor.newInstance()
                .authPreemptive(dispatcherHttpHostWithAuth)
                .auth(dispatcherHttpHostWithAuth, restUsername, restPassword.toCharArray());
    }

    public ReplicationData.ReplicationAsset getData(String uri, Class clazz, @Nullable List<NameValuePair> nvps, Function<URI, Request> requestFunc) {
        if (S.b(globals.dispatcher.asset.sourceUrl) || S.b(globals.dispatcher.asset.username) || S.b(globals.dispatcher.asset.password) ) {
            return new ReplicationData.AssetAcquiringError(
                    S.f("Error in configuration of asset server, some parameter(s) is blank, assetSourceUrl: %s, assetUsername: %s, assetPassword is blank: %s",
                    globals.dispatcher.asset.sourceUrl, globals.dispatcher.asset.username, S.b(globals.dispatcher.asset.password)));
        }
        try {
            final String url = globals.dispatcher.asset.sourceUrl + uri;

            final URIBuilder builder = new URIBuilder(url).setCharset(StandardCharsets.UTF_8);
            if (nvps!=null) {
                builder.addParameters(nvps);
            }
            final URI build = builder.build();
            final Request request = requestFunc.apply(build);

            RestUtils.addHeaders(request);
            Response response = getExecutor(globals.dispatcher.asset.sourceUrl, globals.dispatcher.asset.username, globals.dispatcher.asset.password)
                    .execute(request);

            final HttpResponse httpResponse = response.returnResponse();
            final Content content = response.returnContent();
            if (httpResponse.getCode()!=200) {
                log.error("Server response:\n" + content.asString(StandardCharsets.UTF_8));
                return new ReplicationData.AssetAcquiringError( S.f("Error while accessing url %s, http status code: %d",
                        globals.dispatcher.asset.sourceUrl, httpResponse.getCode()));
            }
            if (content != Content.NO_CONTENT) {
                return getReplicationAsset(content.asStream(), clazz);
            }
            else {
                return new ReplicationData.AssetAcquiringError( S.f("Entry is null, url %s",
                        globals.dispatcher.asset.sourceUrl));
            }
        }
        catch (ConnectTimeoutException | HttpHostConnectException th) {
            log.error("Error: {}", th.getMessage());
            return new ReplicationData.AssetAcquiringError( S.f("Error while accessing url %s, error message: %s",
                    globals.dispatcher.asset.sourceUrl, th.getMessage()));
        }
        catch (Throwable th) {
            log.error("Error", th);
            return new ReplicationData.AssetAcquiringError( S.f("Error while accessing url %s, error message: %s",
                    globals.dispatcher.asset.sourceUrl, th.getMessage()));
        }

    }

    @SuppressWarnings("unchecked")
    private static ReplicationData.ReplicationAsset getReplicationAsset(InputStream content, Class clazz) throws java.io.IOException {
        Object assetResponse = JsonUtils.getMapper().readValue(content, clazz);
        return (ReplicationData.ReplicationAsset)assetResponse;
    }
}

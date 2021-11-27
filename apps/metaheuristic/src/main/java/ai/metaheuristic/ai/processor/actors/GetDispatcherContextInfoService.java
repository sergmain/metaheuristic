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

package ai.metaheuristic.ai.processor.actors;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.data.DispatcherData;
import ai.metaheuristic.ai.processor.DispatcherContextInfoHolder;
import ai.metaheuristic.ai.processor.DispatcherLookupExtendedService;
import ai.metaheuristic.ai.processor.net.HttpClientExecutor;
import ai.metaheuristic.ai.processor.tasks.GetDispatcherContextInfoTask;
import ai.metaheuristic.ai.utils.JsonUtils;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.HttpHostConnectException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * @author Serge
 * Date: 1/2/2021
 * Time: 10:38 AM
 */
@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor
public class GetDispatcherContextInfoService extends AbstractTaskQueue<GetDispatcherContextInfoTask> implements QueueProcessor {

    private final Globals globals;

    private final DispatcherLookupExtendedService dispatcherLookupExtendedService;
    @Override
    public void process() {
        if (globals.testing) {
            return;
        }
        if (!globals.processor.enabled) {
            return;
        }

        GetDispatcherContextInfoTask task;
        while ((task = poll()) != null) {

            final DispatcherLookupParamsYaml.AssetManager assetManager = dispatcherLookupExtendedService.getAssetManager(task.assetManagerUrl);
            if (assetManager==null) {
                log.error("#806.020 assetManager server wasn't found for url {}", task.assetManagerUrl.url);
                continue;
            }

            try {
                final String targetUrl = task.assetManagerUrl.url + Consts.REST_ASSET_URL + "/context-info";
                final String randomPartUri = '/' + UUID.randomUUID().toString().substring(0, 8);

                final URIBuilder builder = new URIBuilder(targetUrl + randomPartUri).setCharset(StandardCharsets.UTF_8);

                final Request request = Request.Get(builder.build()).connectTimeout(5000).socketTimeout(20000);

                RestUtils.addHeaders(request);

                Response response = HttpClientExecutor.getExecutor(task.assetManagerUrl.url, assetManager.username, assetManager.password).execute(request);
                String json = response.returnContent().asString(StandardCharsets.UTF_8);

                DispatcherData.DispatcherContextInfo contextInfo = JsonUtils.getMapper().readValue(json, DispatcherData.DispatcherContextInfo.class);
                DispatcherContextInfoHolder.put(task.assetManagerUrl, contextInfo);
            }
            catch (HttpResponseException e) {
                if (e.getStatusCode()== HttpServletResponse.SC_FORBIDDEN) {
                    log.warn("#806.200 Access denied to url {}", assetManager.url);
                }
                else if (e.getStatusCode()== HttpServletResponse.SC_NOT_FOUND) {
                    log.warn("#806.203 Url {} wasn't found. Need to check the dispatcher.yaml config file", assetManager.url);
                }
                else if (e.getStatusCode()== HttpServletResponse.SC_GONE) {
                    log.warn("#806.205 Functions wasn't found at {}", assetManager.url);
                }
                else if (e.getStatusCode()== HttpServletResponse.SC_CONFLICT) {
                    log.warn("#806.210 Functions are broken and need to be recreated");
                }
                else if (e.getStatusCode()== HttpServletResponse.SC_SERVICE_UNAVAILABLE) {
                    log.warn("#806.210 Service Unavailable for url {}", assetManager.url);
                }
                else {
                    log.error("#806.220 HttpResponseException for url: "+assetManager.url+ ", code: "+ e.getStatusCode(), e);
                }
            }
            catch (HttpHostConnectException e) {
                log.error("#806.170 HttpHostConnectException: {}, assetManagerUrl: {}", e.toString(), assetManager.url);
            }
            catch (SocketTimeoutException e) {
                log.error("#806.175 SocketTimeoutException: {}, assetManagerUrl: {}", e.toString(), assetManager.url);
            }
            catch (IOException e) {
                log.error(S.f("#806.180 IOException, assetManagerUrl: %s", assetManager.url), e);
            }
            catch (Throwable th) {
                log.error(S.f("#806.190 Throwable, assetManagerUrl: %s", assetManager.url), th);
            }
        }
    }
}

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

package ai.metaheuristic.ai.processor.function;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.processor.net.HttpClientExecutor;
import ai.metaheuristic.commons.utils.JsonUtils;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.api.data.replication.ReplicationApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.TaskParamsUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Timeout;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * @author Serge
 * Date: 10/6/2019
 * Time: 4:45 PM
 */
@Slf4j
public class ProcessorFunctionUtils {

    public enum ConfigStatus {ok, error, not_found}

    @Data
    public static class DownloadedFunctionConfigStatus {
        public TaskParamsYaml.FunctionConfig functionConfig;
        public ConfigStatus status;
    }

    @Data
    public static class DownloadedFunctionConfigsStatus {
        public ReplicationApiData.FunctionConfigsReplication functionConfigs;
        public ConfigStatus status;
    }

    public static DownloadedFunctionConfigStatus downloadFunctionConfig(DispatcherLookupParamsYaml.AssetManager assetManager, String functionCode) {

        // 999 - fake processorId for backward compatibility
        final String functionConfigUrl = assetManager.url + Consts.REST_ASSET_URL + "/function-config/999";
        final String randomPartUri = '/' + UUID.randomUUID().toString().substring(0, 8);

        final DownloadedFunctionConfigStatus functionConfigStatus = new DownloadedFunctionConfigStatus();
        functionConfigStatus.status = ConfigStatus.error;
        try {
            final URI uri = new URIBuilder(functionConfigUrl + randomPartUri)
                    .setCharset(StandardCharsets.UTF_8)
                    .addParameter("code", functionCode).build();

            final Request request = Request.get(uri).connectTimeout(Timeout.ofSeconds(5));//.socketTimeout(20000);

            RestUtils.addHeaders(request);

            Response response = HttpClientExecutor.getExecutor(assetManager.url, assetManager.username, assetManager.password).execute(request);
            String yaml = response.returnContent().asString(StandardCharsets.UTF_8);

            functionConfigStatus.functionConfig = TaskParamsUtils.toFunctionConfig(FunctionConfigYamlUtils.UTILS.to(yaml));
            functionConfigStatus.status = ConfigStatus.ok;

        } catch (HttpResponseException e) {
            if (e.getStatusCode()== HttpServletResponse.SC_FORBIDDEN) {
                log.warn("#813.200 Access denied to url {}", functionConfigUrl);
            }
            else if (e.getStatusCode()== HttpServletResponse.SC_NOT_FOUND) {
                functionConfigStatus.status = ConfigStatus.not_found;
                log.warn("#813.220 Url {} wasn't found. Need to check the dispatcher.yaml config file", assetManager.url);
            }
            else if (e.getStatusCode()== HttpServletResponse.SC_GONE) {
                functionConfigStatus.status = ConfigStatus.not_found;
                log.warn("#813.240 Function with code {} wasn't found", functionCode);
            }
            else if (e.getStatusCode()== HttpServletResponse.SC_CONFLICT) {
                log.warn("#813.260 Function with id {} is broken and need to be recreated", functionCode);
            }
            else {
                log.error("#813.280 HttpResponseException", e);
            }
        } catch (SocketTimeoutException e) {
            log.error("#813.300 SocketTimeoutException: {}, function: {}, assetManagerUrl: {}", e.toString(), functionCode, assetManager.url);
        } catch (IOException e) {
            log.error(S.f("#813.320 IOException, function: %s, assetManagerUrl: %s",functionCode, assetManager.url), e);
        } catch (Throwable th) {
            log.error(S.f("#813.340 Throwable, function: %s, assetManagerUrl: %s",functionCode, assetManager.url), th);
        }
        return functionConfigStatus;
    }

    public static DownloadedFunctionConfigsStatus downloadFunctionConfigs(
            String dispatcherUrl,
            DispatcherLookupParamsYaml.AssetManager asset, String processorId) {

        final String functionConfigsUrl = asset.url + Consts.REST_ASSET_URL + "/function-configs/" + processorId;
        final String randomPartUri =  '/' + UUID.randomUUID().toString().substring(0, 8);

        final DownloadedFunctionConfigsStatus functionConfigStatus = new DownloadedFunctionConfigsStatus();
        functionConfigStatus.status = ConfigStatus.error;
        try {
            final Request request = Request.get(functionConfigsUrl + randomPartUri).connectTimeout(Timeout.ofSeconds(5)); //.socketTimeout(20000);

            RestUtils.addHeaders(request);

            Response response = HttpClientExecutor.getExecutor(asset.url, asset.username, asset.password).execute(request);
            String yaml = response.returnContent().asString(StandardCharsets.UTF_8);

            functionConfigStatus.functionConfigs = JsonUtils.getMapper().readValue(yaml, ReplicationApiData.FunctionConfigsReplication.class);
            functionConfigStatus.status = ConfigStatus.ok;
        }
        catch (HttpResponseException e) {
            if (e.getStatusCode()== HttpServletResponse.SC_FORBIDDEN) {
                log.warn("#813.360 Access denied to url {}", functionConfigsUrl);
            }
            else if (e.getStatusCode()== HttpServletResponse.SC_NOT_FOUND) {
                functionConfigStatus.status = ConfigStatus.not_found;
                log.warn("#813.380 Url {} wasn't found. Need to check the dispatcher.yaml config file", asset.url);
            }
            else if (e.getStatusCode()== HttpServletResponse.SC_GONE) {
                functionConfigStatus.status = ConfigStatus.not_found;
                log.warn("#813.400 Functions wasn't found");
            }
            else if (e.getStatusCode()== HttpServletResponse.SC_CONFLICT) {
                log.warn("#813.420 Functions are broken and need to be recreated");
            }
            else {
                log.error("#813.440 HttpResponseException", e);
            }
        }
        catch (SocketTimeoutException e) {
            log.error("#813.460 SocketTimeoutException: {}, dispatcher: {}, assetManagerUrl: {}", e.toString(), dispatcherUrl, asset.url);
        }
        catch (IOException e) {
            log.error(S.f("#813.480 IOException, dispatcher: %s, assetManagerUrl: %s", dispatcherUrl, asset.url), e);
        }
        catch (Throwable th) {
            log.error(S.f("#813.500 Throwable, dispatcher: %s, assetManagerUrl: %s", dispatcherUrl, asset.url), th);
        }
        return functionConfigStatus;
    }

}

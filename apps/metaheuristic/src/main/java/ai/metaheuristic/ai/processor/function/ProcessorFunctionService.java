/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
import ai.metaheuristic.ai.utils.JsonUtils;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupConfig;
import ai.metaheuristic.api.data.replication.ReplicationApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.TaskParamsUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
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
@SuppressWarnings("DuplicatedCode")
@Slf4j
@Service
@Profile("processor")
public class ProcessorFunctionService {

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

    public DownloadedFunctionConfigStatus downloadFunctionConfig(
            String dispatcherUrl,
            DispatcherLookupConfig.Asset asset, String functionCode, String processorId) {

        final String functionChecksumUrl = asset.url + Consts.REST_ASSET_URL + "/function-config/" + processorId;
        final String randomPartUri = '/' + UUID.randomUUID().toString().substring(0, 8);

        final DownloadedFunctionConfigStatus functionConfigStatus = new DownloadedFunctionConfigStatus();
        functionConfigStatus.status = ConfigStatus.error;
        try {
            final URI uri = new URIBuilder(functionChecksumUrl + randomPartUri)
                    .setCharset(StandardCharsets.UTF_8)
                    .addParameter("code", functionCode).build();

            final Request request = Request.Get(uri).connectTimeout(5000).socketTimeout(20000);

/*
            final Request request = Request.Post(functionChecksumUrl + randomPartUri)
                    .bodyForm(Form.form()
                            .add("code", functionCode)
                            .build(), StandardCharsets.UTF_8)
                    .connectTimeout(5000)
                    .socketTimeout(20000);
*/

            RestUtils.addHeaders(request);

            Response response = HttpClientExecutor.getExecutor(asset.url, asset.username, asset.password).execute(request);
            String yaml = response.returnContent().asString(StandardCharsets.UTF_8);

            functionConfigStatus.functionConfig = TaskParamsUtils.toFunctionConfig(FunctionConfigYamlUtils.BASE_YAML_UTILS.to(yaml));
            functionConfigStatus.status = ConfigStatus.ok;

        } catch (HttpResponseException e) {
            if (e.getStatusCode()== HttpServletResponse.SC_FORBIDDEN) {
                log.warn("#813.200 Access denied to url {}", functionChecksumUrl);
            }
            else if (e.getStatusCode()== HttpServletResponse.SC_NOT_FOUND) {
                functionConfigStatus.status = ConfigStatus.not_found;
                log.warn("#813.203 Url {} wasn't found. Need to check the dispatcher.yaml config file", asset.url);
            }
            else if (e.getStatusCode()== HttpServletResponse.SC_GONE) {
                functionConfigStatus.status = ConfigStatus.not_found;
                log.warn("#813.205 Function with code {} wasn't found", functionCode);
            }
            else if (e.getStatusCode()== HttpServletResponse.SC_CONFLICT) {
                log.warn("#813.210 Function with id {} is broken and need to be recreated", functionCode);
            }
            else {
                log.error("#813.220 HttpResponseException", e);
            }
        } catch (SocketTimeoutException e) {
            log.error("#813.170 SocketTimeoutException: {}, function: {}, dispatcher: {}, assetUrl: {}", e.toString(), functionCode, dispatcherUrl, asset.url);
        } catch (IOException e) {
            log.error(S.f("#813.180 IOException, function: %s, dispatcher: %s, assetUrl: %s",functionCode, dispatcherUrl), e);
        } catch (Throwable th) {
            log.error(S.f("#813.190 Throwable, function: %s, dispatcher: %s, assetUrl: %s",functionCode, dispatcherUrl, asset.url), th);
        }
        return functionConfigStatus;
    }

    public DownloadedFunctionConfigsStatus downloadFunctionConfigs(
            String dispatcherUrl,
            DispatcherLookupConfig.Asset asset, String processorId) {

        final String functionConfigsUrl = asset.url + Consts.REST_ASSET_URL + "/function-configs/" + processorId;
        final String randomPartUri =  '/' + UUID.randomUUID().toString().substring(0, 8);

        final DownloadedFunctionConfigsStatus functionConfigStatus = new DownloadedFunctionConfigsStatus();
        functionConfigStatus.status = ConfigStatus.error;
        try {
            final Request request = Request.Get(functionConfigsUrl + randomPartUri).connectTimeout(5000).socketTimeout(20000);

            RestUtils.addHeaders(request);

            Response response = HttpClientExecutor.getExecutor(asset.url, asset.username, asset.password).execute(request);
            String yaml = response.returnContent().asString(StandardCharsets.UTF_8);

            functionConfigStatus.functionConfigs = JsonUtils.getMapper().readValue(yaml, ReplicationApiData.FunctionConfigsReplication.class);
            functionConfigStatus.status = ConfigStatus.ok;
        }
        catch (HttpResponseException e) {
            if (e.getStatusCode()== HttpServletResponse.SC_FORBIDDEN) {
                log.warn("#813.200 Access denied to url {}", functionConfigsUrl);
            }
            else if (e.getStatusCode()== HttpServletResponse.SC_NOT_FOUND) {
                functionConfigStatus.status = ConfigStatus.not_found;
                log.warn("#813.203 Url {} wasn't found. Need to check the dispatcher.yaml config file", asset.url);
            }
            else if (e.getStatusCode()== HttpServletResponse.SC_GONE) {
                functionConfigStatus.status = ConfigStatus.not_found;
                log.warn("#813.205 Functions wasn't found");
            }
            else if (e.getStatusCode()== HttpServletResponse.SC_CONFLICT) {
                log.warn("#813.210 Functions are broken and need to be recreated");
            }
            else {
                log.error("#813.220 HttpResponseException", e);
            }
        }
        catch (SocketTimeoutException e) {
            log.error("#813.170 SocketTimeoutException: {}, dispatcher: {}, assetUrl: {}", e.toString(), dispatcherUrl, asset.url);
        }
        catch (IOException e) {
            log.error(S.f("#813.180 IOException, dispatcher: %s, assetUrl: %s", dispatcherUrl), e);
        }
        catch (Throwable th) {
            log.error(S.f("#813.190 Throwable, dispatcher: %s, assetUrl: %s", dispatcherUrl, asset.url), th);
        }
        return functionConfigStatus;
    }

}

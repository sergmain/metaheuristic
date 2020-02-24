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
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupConfig;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.TaskParamsUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * @author Serge
 * Date: 10/6/2019
 * Time: 4:45 PM
 */
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

    public DownloadedFunctionConfigStatus downloadFunctionConfig(
            String dispatcherUrl,
            DispatcherLookupConfig.Asset asset, String functionCode, String processorId) {

        final String functionChecksumUrl = asset.url + Consts.REST_ASSET_URL + "/function-config";
        final String randomPartUri = '/' + UUID.randomUUID().toString().substring(0, 8) +'-' + processorId;

        final DownloadedFunctionConfigStatus functionConfigStatus = new DownloadedFunctionConfigStatus();
        functionConfigStatus.status = ConfigStatus.error;
        try {
            final Request request = Request.Post(functionChecksumUrl + randomPartUri)
                    .bodyForm(Form.form()
                            .add("code", functionCode)
                            .build(), StandardCharsets.UTF_8)
                    .connectTimeout(5000)
                    .socketTimeout(20000);

            RestUtils.addHeaders(request);

            Response response = HttpClientExecutor.getExecutor(asset.url, asset.username, asset.password).execute(request);
            String yaml = response.returnContent().asString(StandardCharsets.UTF_8);

            functionConfigStatus.functionConfig = TaskParamsUtils.toFunctionConfig(FunctionConfigYamlUtils.BASE_YAML_UTILS.to(yaml));
            functionConfigStatus.status = ConfigStatus.ok;

        } catch (HttpResponseException e) {
            if (e.getStatusCode()== HttpServletResponse.SC_FORBIDDEN) {
                log.warn("#813.200 Access denied to url {}", functionChecksumUrl);
            }
            else if (e.getStatusCode()== HttpServletResponse.SC_GONE) {
                functionConfigStatus.status = ConfigStatus.not_found;
                log.warn("#813.200 Function with code {} wasn't found", functionCode);
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

}
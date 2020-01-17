/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.station.snippet;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.station.net.HttpClientExecutor;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.launchpad_lookup.LaunchpadLookupConfig;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.TaskParamsUtils;
import ai.metaheuristic.commons.yaml.snippet.SnippetConfigYamlUtils;
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
@Profile("station")
public class StationSnippetService {

    public enum ConfigStatus {ok, error, not_found}

    @Data
    public static class DownloadedSnippetConfigStatus {
        public TaskParamsYaml.SnippetConfig snippetConfig;
        public ConfigStatus status;
    }

    public DownloadedSnippetConfigStatus downloadSnippetConfig(
            LaunchpadLookupConfig.Asset asset, String snippetCode, String stationId) {

        final String snippetChecksumUrl = asset.url + Consts.REST_ASSET_URL + "/snippet-config";
        final String randomPartUri = '/' + UUID.randomUUID().toString().substring(0, 8) +'-' + stationId;

        final DownloadedSnippetConfigStatus snippetConfigStatus = new DownloadedSnippetConfigStatus();
        snippetConfigStatus.status = ConfigStatus.error;
        try {
            final Request request = Request.Post(snippetChecksumUrl + randomPartUri)
                    .bodyForm(Form.form()
                            .add("code", snippetCode)
                            .build(), StandardCharsets.UTF_8)
                    .connectTimeout(5000)
                    .socketTimeout(20000);

            RestUtils.addHeaders(request);

            Response response = HttpClientExecutor.getExecutor(asset.url, asset.username, asset.password).execute(request);
            String yaml = response.returnContent().asString(StandardCharsets.UTF_8);

            snippetConfigStatus.snippetConfig = TaskParamsUtils.toSnippetConfig(SnippetConfigYamlUtils.BASE_YAML_UTILS.to(yaml));
            snippetConfigStatus.status = ConfigStatus.ok;

        } catch (HttpResponseException e) {
            if (e.getStatusCode()== HttpServletResponse.SC_FORBIDDEN) {
                log.warn("#813.200 Access denied to url {}", snippetChecksumUrl);
            }
            else if (e.getStatusCode()== HttpServletResponse.SC_GONE) {
                snippetConfigStatus.status = ConfigStatus.not_found;
                log.warn("#813.200 Snippet with code {} wasn't found", snippetCode);
            }
            else if (e.getStatusCode()== HttpServletResponse.SC_CONFLICT) {
                log.warn("#813.210 Snippet with id {} is broken and need to be recreated", snippetCode);
            }
            else {
                log.error("#813.220 HttpResponseException", e);
            }
        } catch (SocketTimeoutException e) {
            log.error("#813.170 SocketTimeoutException: {}, snippet: {}, launchpad: {}", e.toString(), snippetCode, asset.url);
        } catch (IOException e) {
            log.error(S.f("#813.180 IOException, snippet: %s, launchpad: %s",snippetCode, asset.url), e);
        } catch (Throwable th) {
            log.error(S.f("#813.190 Throwable, snippet: %s, launchpad: %s",snippetCode, asset.url), th);
        }
        return snippetConfigStatus;
    }

}

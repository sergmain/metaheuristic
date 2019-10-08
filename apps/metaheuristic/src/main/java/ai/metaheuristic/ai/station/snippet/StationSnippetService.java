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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.S;
import ai.metaheuristic.ai.station.actors.DownloadSnippetActor;
import ai.metaheuristic.ai.station.net.HttpClientExecutor;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.launchpad_lookup.LaunchpadLookupConfig;
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.yaml.snippet.SnippetConfigUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
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
public class StationSnippetService {

    public enum ConfigStatus {ok, error, not_found}

    @Data
    public static class DownloadedSnippetConfigStatus {
        public SnippetApiData.SnippetConfig snippetConfig;
        public ConfigStatus status;
    }

    public DownloadedSnippetConfigStatus downloadSnippetConfig(
            LaunchpadLookupConfig.LaunchpadLookup launchpad, String payloadRestUrl, String snippetCode, String stationId) {

        final String snippetChecksumUrl = payloadRestUrl + "/snippet-config";
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

            Response response;
            if (launchpad.securityEnabled) {
                response = HttpClientExecutor.getExecutor(launchpad.url, launchpad.restUsername, launchpad.restPassword).execute(request);
            } else {
                response = request.execute();
            }
            String yaml = response.returnContent().asString(StandardCharsets.UTF_8);

            snippetConfigStatus.snippetConfig = SnippetConfigUtils.to(yaml);
            snippetConfigStatus.status = ConfigStatus.ok;

        } catch (HttpResponseException e) {
            if (e.getStatusCode()== HttpServletResponse.SC_GONE) {
                snippetConfigStatus.status = ConfigStatus.not_found;
                log.warn("#811.200 Snippet with code {} wasn't found", snippetCode);
            }
            else if (e.getStatusCode()== HttpServletResponse.SC_CONFLICT) {
                log.warn("#811.210 Snippet with id {} is broken and need to be recreated", snippetCode);
            }
            else {
                log.error("#811.220 HttpResponseException", e);
            }
        } catch (SocketTimeoutException e) {
            log.error("#811.170 SocketTimeoutException: {}, snippet: {}, launchpad: {}", e.toString(), snippetCode, launchpad.url);
        } catch (IOException e) {
            log.error(S.f("#811.180 IOException, snippet: %s, launchpad: %s",snippetCode, launchpad.url), e);
        } catch (Throwable th) {
            log.error(S.f("#811.190 Throwable, snippet: %s, launchpad: %s",snippetCode, launchpad.url), th);
        }
        return snippetConfigStatus;
    }

}

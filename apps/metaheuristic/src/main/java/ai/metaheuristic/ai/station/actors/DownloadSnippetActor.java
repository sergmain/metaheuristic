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
package ai.metaheuristic.ai.station.actors;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.S;
import ai.metaheuristic.ai.resource.AssetFile;
import ai.metaheuristic.ai.resource.ResourceUtils;
import ai.metaheuristic.ai.station.LaunchpadLookupExtendedService;
import ai.metaheuristic.ai.station.MetadataService;
import ai.metaheuristic.ai.station.StationTaskService;
import ai.metaheuristic.ai.station.net.HttpClientExecutor;
import ai.metaheuristic.ai.station.tasks.DownloadSnippetTask;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.launchpad_lookup.LaunchpadLookupConfig;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.metadata.SnippetDownloadStatusYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.utils.checksum.CheckSumAndSignatureStatus;
import ai.metaheuristic.commons.utils.checksum.ChecksumWithSignatureUtils;
import ai.metaheuristic.commons.yaml.snippet.SnippetConfigUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@Slf4j
@Profile("station")
@RequiredArgsConstructor
public class DownloadSnippetActor extends AbstractTaskQueue<DownloadSnippetTask> {

    private final Globals globals;
    private final MetadataService metadataService;
    private final StationTaskService stationTaskService;
    private final LaunchpadLookupExtendedService launchpadLookupExtendedService;

    private enum ConfigStatus {ok, error}

    @Data
    private static class DownloadSnippetConfigStatus {
        public SnippetApiData.SnippetConfig snippetConfig;
        public ConfigStatus status;
    }

    @SuppressWarnings("Duplicates")
    public void downloadSnippets() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }

        DownloadSnippetTask task;
        while((task = poll())!=null) {
            final String snippetCode = task.snippetCode;
            final String payloadRestUrl = task.launchpad.url + Consts.REST_V1_URL + Consts.PAYLOAD_REST_URL;

            final SnippetDownloadStatusYaml.Status snippetDownloadStatus = metadataService.getSnippetDownloadStatuses(task.launchpad.url, snippetCode);
            if (snippetDownloadStatus.sourcing!=EnumsApi.SnippetSourcing.launchpad) {
                log.warn("#811.010 Snippet {} can't be downloaded because sourcing isn't 'launchpad'.", snippetCode);
                continue;
            }

            // task.snippetConfig is null when we are downloading a snippet proactively, without any task
            SnippetApiData.SnippetConfig snippetConfig = task.snippetConfig;
            if (snippetConfig ==null) {
                DownloadSnippetConfigStatus downloadSnippetConfigStatus = downloadSnippetConfig(task.launchpad, payloadRestUrl, snippetCode, task.stationId);
                if (downloadSnippetConfigStatus.status!= ConfigStatus.ok) {
                    metadataService.setSnippetState(task.launchpad.url, snippetCode, Enums.SnippetState.snippet_config_error);
                    continue;
                }
                snippetConfig = downloadSnippetConfigStatus.snippetConfig;
            }

            // check requirements of signature
            Checksum checksum = null;
            if (task.launchpad.acceptOnlySignedSnippets) {
                if (S.b(snippetConfig.checksum)) {
                    metadataService.setSnippetState(task.launchpad.url, snippetCode, Enums.SnippetState.signature_not_found);
                    continue;
                }
                checksum = Checksum.fromJson(snippetConfig.checksum);
                boolean isSignature = checksum.checksums.entrySet().stream().anyMatch(e -> e.getKey().isSign);
                if (!isSignature) {
                    metadataService.setSnippetState(task.launchpad.url, snippetCode, Enums.SnippetState.signature_not_found);
                    continue;
                }
            }

            final Metadata.LaunchpadInfo launchpadCode = metadataService.launchpadUrlAsCode(task.launchpad.url);
            final File baseResourceDir = stationTaskService.prepareBaseResourceDir(launchpadCode);
            final AssetFile assetFile = ResourceUtils.prepareSnippetFile(baseResourceDir, snippetCode, snippetConfig.file);

            switch (snippetDownloadStatus.snippetState) {
                case none:
                    if (assetFile.isError) {
                        metadataService.setSnippetState(task.launchpad.url, snippetCode, Enums.SnippetState.asset_error);
                        continue;
                    }
                    break;
                case ok:
                    log.error("#811.010 Unexpected state of snippet {}, launchpad {}, will be resetted to none", snippetCode, task.launchpad.url);
                    metadataService.setSnippetState(task.launchpad.url, snippetCode, Enums.SnippetState.none);
                    break;
                case signature_wrong:
                case signature_not_found:
                case checksum_wrong:
                case not_supported_os:
                case not_found:
                case asset_error:
                case download_error:
                case snippet_config_error:
                    log.warn("#811.010 Snippet can't be downloaded. The current status is {}", snippetDownloadStatus.snippetState);
                    continue;
                case ready:
                    if (assetFile.isError || !assetFile.isContent ) {
                        log.warn("#811.010 Snippet is broken. Set state to asset_error");
                        metadataService.setSnippetState(task.launchpad.url, snippetCode, Enums.SnippetState.asset_error);
                    }
                    continue;
            }

            try {

                File snippetTempFile = new File(assetFile.file.getAbsolutePath() + ".tmp");

                final String targetUrl = payloadRestUrl + "/resource/" + EnumsApi.BinaryDataType.SNIPPET;

                String mask = assetFile.file.getName() + ".%s.tmp";
                File dir = assetFile.file.getParentFile();
                Enums.SnippetState resourceState = Enums.SnippetState.none;
                int idx = 0;
                do {
                    final String randomPartUri = '/' + UUID.randomUUID().toString().substring(0, 8) +'-' + task.stationId;
                    try {
                        final URIBuilder builder = new URIBuilder(targetUrl + randomPartUri).setCharset(StandardCharsets.UTF_8)
                                .addParameter("stationId", task.stationId)
                                .addParameter("code", task.snippetCode)
                                .addParameter("chunkSize", task.chunkSize!=null ? task.chunkSize.toString() : "")
                                .addParameter("chunkNum", Integer.toString(idx));

                        final Request request = Request.Get(builder.build())
                                .connectTimeout(5000)
                                .socketTimeout(20000);

                        RestUtils.addHeaders(request);

                        Response response;
                        if (task.launchpad.securityEnabled) {
                            response = HttpClientExecutor.getExecutor(task.launchpad.url, task.launchpad.restUsername, task.launchpad.restPassword).execute(request);
                        }
                        else {
                            response = request.execute();
                        }
                        File partFile = new File(dir, String.format(mask, idx));

                        final HttpResponse httpResponse = response.returnResponse();
                        try (final FileOutputStream out = new FileOutputStream(partFile)) {
                            final HttpEntity entity = httpResponse.getEntity();
                            if (entity != null) {
                                entity.writeTo(out);
                            }
                            else {
                                log.warn("#811.045 http entity is null");
                            }
                        }
                        final Header[] headers = httpResponse.getAllHeaders();
                        if (!DownloadUtils.isChunkConsistent(partFile, headers)) {
                            log.error("#811.047 error while downloading chunk of snippet {}, size is different", snippetCode);
                            metadataService.setSnippetState(task.launchpad.url, snippetCode, Enums.SnippetState.download_error);
                            resourceState = Enums.SnippetState.download_error;
                            break;
                        }
                        if (DownloadUtils.isLastChunk(headers)) {
                            resourceState = Enums.SnippetState.ok;
                            break;
                        }
                        if (partFile.length()==0) {
                            resourceState = Enums.SnippetState.ok;
                            break;
                        }
                    } catch (HttpResponseException e) {
                        if (e.getStatusCode() == HttpServletResponse.SC_GONE) {
                            final String es = S.f("#811.050 Snippet %s wasn't found on launchpad %s.", task.snippetCode, task.launchpad.url);
                            log.warn(es);
                            metadataService.setSnippetState(task.launchpad.url, snippetCode, Enums.SnippetState.not_found);
//                            stationTaskService.markAsFinishedWithError(task.launchpad.url, task.getTaskId(), es);
                            resourceState = Enums.SnippetState.not_found;
                            break;
                        }
                        else if (e.getStatusCode() == HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE ) {
                            final String es = S.f("#811.060 Unknown error with a snippet %s from launchpad %s.", task.snippetCode, task.launchpad.url);
                            log.warn(es);
                            metadataService.setSnippetState(task.launchpad.url, snippetCode, Enums.SnippetState.download_error);
//                            stationTaskService.markAsFinishedWithError(task.launchpad.url, task.getTaskId(), es);
                            resourceState = Enums.SnippetState.download_error;
                            break;
                        }
                        else if (e.getStatusCode() == HttpServletResponse.SC_NOT_ACCEPTABLE) {
                            final String es = S.f("#811.070 Unknown error with a resource %s from launchpad %s.", task.snippetCode, task.launchpad.url);
                            log.warn(es);
                            metadataService.setSnippetState(task.launchpad.url, snippetCode, Enums.SnippetState.download_error);
//                            stationTaskService.markAsFinishedWithError(task.launchpad.url, task.getTaskId(), es);
                            resourceState = Enums.SnippetState.download_error;
                            break;
                        }
                    }
                    idx++;
                } while (idx<1000);
                if (resourceState == Enums.SnippetState.none) {
                    log.error("#811.080 something wrong, is file too big or chunkSize too small? chunkSize: {}", task.chunkSize);
                    continue;
                }
                else if (resourceState == Enums.SnippetState.download_error || resourceState == Enums.SnippetState.not_found) {
                    log.warn("#811.082 snippet {} can't be acquired, state: {}", snippetCode, resourceState);
                    continue;
                }

                try (FileOutputStream fos = new FileOutputStream(snippetTempFile)) {
                    for (int i = 0; i <= idx; i++) {
                        final File input = new File(assetFile.file.getAbsolutePath() + "." + i + ".tmp");
                        if (input.length()==0) {
                            continue;
                        }
                        FileUtils.copyFile(input, fos);
                    }
                }

                CheckSumAndSignatureStatus status = new CheckSumAndSignatureStatus(CheckSumAndSignatureStatus.Status.correct, CheckSumAndSignatureStatus.Status.correct);
                if (task.launchpad.acceptOnlySignedSnippets) {
                    try (FileInputStream fis = new FileInputStream(snippetTempFile)) {
                        // IDEA's fck up, can't correctly predict the state of checksum
                        status = ChecksumWithSignatureUtils.verifyChecksumAndSignature(checksum, "Launchpad: "+task.launchpad.url+", snippet: "+snippetCode, fis, true, task.launchpad.createPublicKey());
                    }
                    if (status.signature != CheckSumAndSignatureStatus.Status.correct) {
                        log.warn("#811.100 launchpad.acceptOnlySignedSnippets is {} but snippet {} has the broken signature", task.launchpad.acceptOnlySignedSnippets, snippetCode);
                        metadataService.setSnippetState(task.launchpad.url, snippetCode, Enums.SnippetState.signature_wrong);
                    }
                    else if (status.checksum != CheckSumAndSignatureStatus.Status.correct) {
                        log.warn("#811.100 launchpad.acceptOnlySignedSnippets is {} but snippet {} has the broken signature", task.launchpad.acceptOnlySignedSnippets, snippetCode);
                        metadataService.setSnippetState(task.launchpad.url, snippetCode, Enums.SnippetState.checksum_wrong);
                    }
                }
                if (status.checksum==CheckSumAndSignatureStatus.Status.correct && status.signature==CheckSumAndSignatureStatus.Status.correct) {
                    //noinspection ResultOfMethodCallIgnored
                    snippetTempFile.renameTo(assetFile.file);
                    metadataService.setSnippetState(task.launchpad.url, snippetCode, Enums.SnippetState.ready);
                }
                else {
                    //noinspection ResultOfMethodCallIgnored
                    snippetTempFile.delete();
                }
            }
            catch (HttpResponseException e) {
                logError(snippetCode, e);
            }
            catch (SocketTimeoutException e) {
                log.error("#811.110 SocketTimeoutException: {}", e.toString());
            }
            catch (IOException e) {
                log.error("#811.120 IOException", e);
            } catch (URISyntaxException e) {
                log.error("#811.130 URISyntaxException", e);
            }
        }
    }

    public void prepareSnippetForDownloading() {
        metadataService.getSnippetDownloadStatusYaml().statuses.forEach(o->{
            if (o.sourcing== EnumsApi.SnippetSourcing.launchpad && o.snippetState== Enums.SnippetState.none) {
                final LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad =
                        launchpadLookupExtendedService.lookupExtendedMap.get(o.launchpadUrl);

                Metadata.LaunchpadInfo launchpadInfo = metadataService.launchpadUrlAsCode(o.launchpadUrl);

                DownloadSnippetTask snippetTask = new DownloadSnippetTask(launchpad.config.chunkSize, o.code, null);
                snippetTask.launchpad = launchpad.launchpadLookup;
                snippetTask.stationId = launchpadInfo.stationId;
                add(snippetTask);
            }
        });
    }

    private DownloadSnippetConfigStatus downloadSnippetConfig(
            LaunchpadLookupConfig.LaunchpadLookup launchpad, String payloadRestUrl, String snippetCode, String stationId) {

        final String snippetChecksumUrl = payloadRestUrl + "/snippet-config";
        final String randomPartUri = '/' + UUID.randomUUID().toString().substring(0, 8) +'-' + stationId;

        final DownloadSnippetConfigStatus snippetConfigStatus = new DownloadSnippetConfigStatus();
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
            logError(snippetCode, e);
        } catch (SocketTimeoutException e) {
            log.error("#811.020 SocketTimeoutException: {}", e.toString());
        } catch (IOException e) {
            log.error("#811.030 IOException", e);
        } catch (Throwable th) {
            log.error("#811.040 Throwable", th);
        }
        return snippetConfigStatus;
    }

    private void logError(String snippetCode, HttpResponseException e) {
        if (e.getStatusCode()== HttpServletResponse.SC_GONE) {
            log.warn("#811.140 Snippet with code {} wasn't found", snippetCode);
        }
        else if (e.getStatusCode()== HttpServletResponse.SC_CONFLICT) {
            log.warn("#811.150 Snippet with id {} is broken and need to be recreated", snippetCode);
        }
        else {
            log.error("#811.160 HttpResponseException", e);
        }
    }

}
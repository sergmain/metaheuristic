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
import ai.metaheuristic.ai.resource.AssetFile;
import ai.metaheuristic.ai.station.MetadataService;
import ai.metaheuristic.ai.resource.ResourceUtils;
import ai.metaheuristic.ai.station.StationTaskService;
import ai.metaheuristic.ai.station.net.HttpClientExecutor;
import ai.metaheuristic.ai.station.tasks.DownloadSnippetTask;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.commons.utils.checksum.CheckSumAndSignatureStatus;
import ai.metaheuristic.commons.utils.checksum.ChecksumWithSignatureUtils;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.utils.Checksum;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@Profile("station")
@RequiredArgsConstructor
public class DownloadSnippetActor extends AbstractTaskQueue<DownloadSnippetTask> {

    private final Globals globals;
    private final MetadataService metadataService;
    private final StationTaskService stationTaskService;

    private final Map<String, Boolean> preparedMap = new LinkedHashMap<>();

    @SuppressWarnings("Duplicates")
    public void fixedDelay() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }

        DownloadSnippetTask task;
        while((task = poll())!=null) {
            if (Boolean.TRUE.equals(preparedMap.get(task.getSnippetCode()))) {
                continue;
            }
            final String snippetCode = task.snippetCode;

            final Metadata.LaunchpadInfo launchpadCode = metadataService.launchpadUrlAsCode(task.launchpad.url);
            final File snippetDir = stationTaskService.prepareSnippetDir(launchpadCode);
            final AssetFile assetFile = ResourceUtils.prepareSnippetFile(snippetDir, task.snippetCode, task.filename);
            if (assetFile.isError ) {
                log.warn("#811.010 Resource can't be downloaded. Asset file initialization was failed, {}", assetFile);
                continue;
            }
            if (assetFile.isContent ) {
                log.info("Snippet was already downloaded. Snippet file: {}", assetFile.file.getPath());
                preparedMap.put(snippetCode, true);
                continue;
            }

            final String payloadRestUrl = task.launchpad.url + Consts.REST_V1_URL + Consts.PAYLOAD_REST_URL;

            final String targetUrl = payloadRestUrl + "/resource/" + EnumsApi.BinaryDataType.SNIPPET;
            final String snippetChecksumUrl = payloadRestUrl + "/snippet-checksum";

            try {
                final String randomPartUri = '/' + UUID.randomUUID().toString().substring(0, 8) +
                        '-' + task.stationId +
                        '-' + task.getTaskId() +
                        '-' + URLEncoder.encode(task.snippetCode, StandardCharsets.UTF_8.toString());

                Checksum checksum=null;
                if (task.launchpad.acceptOnlySignedSnippets) {
                    try {

                        final Request request = Request.Post(snippetChecksumUrl + randomPartUri)
                                .bodyForm(Form.form()
                                        .add("stationId", task.stationId)
                                        .add("taskId", Long.toString(task.getTaskId()))
                                        .add("code", task.snippetCode)
                                        .build(), StandardCharsets.UTF_8)
                                .connectTimeout(5000)
                                .socketTimeout(20000);

                        RestUtils.addHeaders(request);

                        Response response;
                        if (task.launchpad.securityEnabled) {
                            response = HttpClientExecutor.getExecutor(task.launchpad.url, task.launchpad.restUsername, task.launchpad.restPassword).execute(request);
                        } else {
                            response = request.execute();
                        }
                        String checksumStr = response.returnContent().asString(StandardCharsets.UTF_8);

                        checksum = Checksum.fromJson(checksumStr);
                    } catch (HttpResponseException e) {
                        logError(snippetCode, e);
                        continue;
                    } catch (SocketTimeoutException e) {
                        log.error("#811.020 SocketTimeoutException: {}", e.toString());
                        continue;
                    } catch (IOException e) {
                        log.error("#811.030 IOException", e);
                        continue;
                    } catch (Throwable th) {
                        log.error("#811.040 Throwable", th);
                        continue;
                    }
                }

                File snippetTempFile = new File(assetFile.file.getAbsolutePath() + ".tmp");

                String mask = assetFile.file.getName() + ".%s.tmp";
                File dir = assetFile.file.getParentFile();
                Enums.ResourceState resourceState = Enums.ResourceState.none;
                int idx = 0;
                do {
                    try {
                        final URIBuilder builder = new URIBuilder(targetUrl + randomPartUri).setCharset(StandardCharsets.UTF_8)
                                .addParameter("stationId", task.stationId)
                                .addParameter("taskId", Long.toString(task.getTaskId()))
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
                            resourceState = Enums.ResourceState.transmitting_error;
                            break;
                        }
                        if (DownloadUtils.isLastChunk(headers)) {
                            resourceState = Enums.ResourceState.ok;
                            break;
                        }
                        if (partFile.length()==0) {
                            resourceState = Enums.ResourceState.ok;
                            break;
                        }
                    } catch (HttpResponseException e) {
                        if (e.getStatusCode() == HttpServletResponse.SC_GONE) {
                            final String es = String.format("#811.050 Resource %s wasn't found on launchpad. Task #%s is finished.", task.snippetCode, task.getTaskId());
                            log.warn(es);
                            stationTaskService.markAsFinishedWithError(task.launchpad.url, task.getTaskId(), es);
                            resourceState = Enums.ResourceState.resource_doesnt_exist;
                            break;
                        }
                        else if (e.getStatusCode() == HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE ) {
                            final String es = String.format("#811.060 Unknown error with a resource %s. Task #%s is finished.", task.snippetCode, task.getTaskId());
                            log.warn(es);
                            stationTaskService.markAsFinishedWithError(task.launchpad.url, task.getTaskId(), es);
                            resourceState = Enums.ResourceState.unknown_error;
                            break;
                        }
                        else if (e.getStatusCode() == HttpServletResponse.SC_NOT_ACCEPTABLE) {
                            final String es = String.format("#811.070 Unknown error with a resource %s. Task #%s is finished.", task.snippetCode, task.getTaskId());
                            log.warn(es);
                            stationTaskService.markAsFinishedWithError(task.launchpad.url, task.getTaskId(), es);
                            resourceState = Enums.ResourceState.unknown_error;
                            break;
                        }
                    }
                    idx++;
                } while (idx<1000);
                if (resourceState == Enums.ResourceState.none) {
                    log.error("#811.080 something wrong, is file too big or chunkSize too small? chunkSize: {}", task.chunkSize);
                    continue;
                }
                else if (resourceState == Enums.ResourceState.unknown_error || resourceState == Enums.ResourceState.resource_doesnt_exist) {
                    log.warn("#811.082 snippet {} can't be acquired, state: {}", snippetCode, resourceState);
                    continue;
                }
                else if (resourceState == Enums.ResourceState.transmitting_error) {
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

                CheckSumAndSignatureStatus.Status isOk = CheckSumAndSignatureStatus.Status.unknown;
                if (task.launchpad.acceptOnlySignedSnippets) {
                    CheckSumAndSignatureStatus status;
                    try (FileInputStream fis = new FileInputStream(snippetTempFile)) {
                        status = ChecksumWithSignatureUtils.verifyChecksumAndSignature(checksum, "Launchpad: "+task.launchpad.url+", snippet: "+snippetCode, fis, true, task.launchpad.createPublicKey());
                    }
                    if ( status.signature == null){
                        log.warn("#811.090 launchpad.acceptOnlySignedSnippets is {} but snippet with code {} doesn't have signature", task.launchpad.acceptOnlySignedSnippets, snippetCode);
                        continue;
                    }
                    if (Boolean.FALSE.equals(status.signature)) {
                        log.warn("#811.100 launchpad.acceptOnlySignedSnippets is {} but snippet {} has the broken signature", task.launchpad.acceptOnlySignedSnippets, snippetCode);
                        continue;
                    }
                    isOk = status.checksum;
                }
                if (isOk==CheckSumAndSignatureStatus.Status.correct) {
                    //noinspection ResultOfMethodCallIgnored
                    snippetTempFile.renameTo(assetFile.file);
                    preparedMap.put(snippetCode, true);
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
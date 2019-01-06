/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.station.actors;

import aiai.ai.Consts;
import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.station.AssetFile;
import aiai.ai.station.MetadataService;
import aiai.ai.utils.ResourceUtils;
import aiai.ai.station.StationTaskService;
import aiai.ai.station.net.HttpClientExecutor;
import aiai.ai.station.tasks.DownloadSnippetTask;
import aiai.ai.utils.RestUtils;
import aiai.ai.utils.checksum.CheckSumAndSignatureStatus;
import aiai.ai.utils.checksum.ChecksumWithSignatureService;
import aiai.ai.yaml.metadata.Metadata;
import aiai.apps.commons.utils.Checksum;
import lombok.extern.slf4j.Slf4j;
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
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@Profile("station")
public class DownloadSnippetActor extends AbstractTaskQueue<DownloadSnippetTask> {

    private final Globals globals;
    private final MetadataService metadataService;
    private final StationTaskService stationTaskService;

    private final Map<String, Boolean> preparedMap = new LinkedHashMap<>();

    public DownloadSnippetActor(Globals globals, MetadataService metadataService, StationTaskService stationTaskService) {
        this.globals = globals;
        this.metadataService = metadataService;
        this.stationTaskService = stationTaskService;
    }

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
                log.warn("Resource can't be downloaded. Asset file initialization was failed, {}", assetFile);
                continue;
            }
            if (assetFile.isContent ) {
                log.info("Snippet was already downloaded. Snippet file: {}", assetFile.file.getPath());
                preparedMap.put(snippetCode, true);
                continue;
            }

            final String restUrl = task.launchpad.url + (task.launchpad.isSecureRestUrl ? Consts.REST_AUTH_URL : Consts.REST_ANON_URL );
            final String payloadRestUrl = restUrl + Consts.PAYLOAD_REST_URL;

            final String targetUrl = payloadRestUrl + "/resource/" + Enums.BinaryDataType.SNIPPET;
            final String snippetChecksumUrl = payloadRestUrl + "/snippet-checksum";


            try {
                final String randomPartUri = '/' + UUID.randomUUID().toString().substring(0, 8) +
                        '-' + task.stationId +
                        '-' + task.getTaskId() +
                        '-' + URLEncoder.encode(task.snippetCode, StandardCharsets.UTF_8.toString());

                Checksum checksum=null;
                if (task.launchpad.isAcceptOnlySignedSnippets) {
                    try {

                        final Request request = Request.Post(snippetChecksumUrl + randomPartUri)
                                .bodyForm(Form.form()
                                        .add("stationId", task.stationId)
                                        .add("taskId", Long.toString(task.getTaskId()))
                                        .add("code", task.snippetCode)
                                        .build(), StandardCharsets.UTF_8)
                                .connectTimeout(5000)
                                .socketTimeout(5000);

                        RestUtils.addHeaders(request);

                        Response response;
                        if (task.launchpad.isSecureRestUrl) {
                            response = HttpClientExecutor.getExecutor(task.launchpad.url, task.launchpad.restUsername, task.launchpad.restToken, task.launchpad.restPassword).execute(request);
                        } else {
                            response = request.execute();
                        }
                        String checksumStr = response.returnContent().asString(StandardCharsets.UTF_8);

                        checksum = Checksum.fromJson(checksumStr);
                    } catch (HttpResponseException e) {
                        logError(snippetCode, e);
                        continue;
                    } catch (SocketTimeoutException e) {
                        log.error("SocketTimeoutException: {}", e.toString());
                        continue;
                    } catch (IOException e) {
                        log.error("IOException", e);
                        continue;
                    } catch (Throwable th) {
                        log.error("Throwable", th);
                        continue;
                    }
                }

                final URIBuilder builder = new URIBuilder(targetUrl + randomPartUri).setCharset(StandardCharsets.UTF_8)
                        .addParameter("stationId", task.stationId)
                        .addParameter("taskId", Long.toString(task.getTaskId()))
                        .addParameter("code", task.snippetCode);


                final Request request = Request.Get(builder.build())
/*
                Request request = Request.Get(targetUrl + randomPartUri)
                        .bodyForm(Form.form()
                                .add("stationId", task.stationId)
                                .add("taskId", Long.toString(task.getTaskId()))
                                .add("code", task.snippetCode)
                                .build(), StandardCharsets.UTF_8)
*/
                        .connectTimeout(5000)
                        .socketTimeout(5000);

                Response response;
                if (task.launchpad.isSecureRestUrl) {
                    response = HttpClientExecutor.getExecutor(task.launchpad.url, task.launchpad.restUsername, task.launchpad.restToken, task.launchpad.restPassword).execute(request);
                }
                else {
                    response = request.execute();
                }
                File snippetTempFile = new File(assetFile.file.getAbsolutePath()+".tmp");
                response.saveContent(snippetTempFile);

                boolean isOk = true;
                if (task.launchpad.isAcceptOnlySignedSnippets) {
                    CheckSumAndSignatureStatus status;
                    try (FileInputStream fis = new FileInputStream(snippetTempFile)) {
                        status = ChecksumWithSignatureService.verifyChecksumAndSignature(checksum, "Snippet "+snippetCode, fis, true, task.launchpad.createPublicKey());
                    }
                    if ( status.isSignatureOk == null){
                        log.warn("launchpad.isAcceptOnlySignedSnippets is {} but snippet with code {} doesn't have signature", task.launchpad.isAcceptOnlySignedSnippets, snippetCode);
                        continue;
                    }
                    if (Boolean.FALSE.equals(status.isSignatureOk)) {
                        log.warn("launchpad.isAcceptOnlySignedSnippets is {} but snippet with code {} has the broken signature", task.launchpad.isAcceptOnlySignedSnippets, snippetCode);
                        continue;
                    }
                    isOk = status.isOk;
                }
                if (isOk) {
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
                log.error("SocketTimeoutException: {}", e.toString());
            }
            catch (IOException e) {
                log.error("IOException", e);
            } catch (URISyntaxException e) {
                log.error("URISyntaxException", e);
            }
        }
    }

    private void logError(String snippetCode, HttpResponseException e) {
        if (e.getStatusCode()== HttpServletResponse.SC_GONE) {
            log.warn("Snippet with code {} wasn't found", snippetCode);
        }
        else if (e.getStatusCode()== HttpServletResponse.SC_CONFLICT) {
            log.warn("Snippet with id {} is broken and need to be recreated", snippetCode);
        }
        else {
            log.error("HttpResponseException", e);
        }
    }

}
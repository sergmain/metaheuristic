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

import aiai.ai.Globals;
import aiai.ai.snippet.SnippetUtils;
import aiai.ai.station.tasks.DownloadSnippetTask;
import aiai.ai.utils.checksum.CheckSumAndSignatureStatus;
import aiai.apps.commons.utils.Checksum;
import aiai.ai.utils.checksum.ChecksumWithSignatureService;
import aiai.apps.commons.utils.DirUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Request;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class DownloadSnippetActor extends AbstractTaskQueue<DownloadSnippetTask> {

    private final Globals globals;
    private final ChecksumWithSignatureService checksumWithSignatureService;

    private String targetUrl;
    private String snippetChecksumUrl;

    private final Map<String, Boolean> preparedMap = new LinkedHashMap<>();

    public DownloadSnippetActor(Globals globals, ChecksumWithSignatureService checksumWithSignatureService) {
        this.globals = globals;
        this.checksumWithSignatureService = checksumWithSignatureService;
    }

    @PostConstruct
    public void postConstruct() {
        if (globals.isStationEnabled) {
            targetUrl = globals.payloadRestUrl + "/snippet";
            snippetChecksumUrl = globals.payloadRestUrl + "/snippet-checksum";
        }
    }

    public void fixedDelay() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }

        File snippetDir = DirUtils.createDir(globals.stationDir, "snippet");
        if (snippetDir==null) {
            System.out.println("Station environment is broken. See log for more information");
            return;
        }

        DownloadSnippetTask task;
        while((task = poll())!=null) {
            if (Boolean.TRUE.equals(preparedMap.get(task.getSnippetCode()))) {
                continue;
            }

            SnippetUtils.SnippetFile snippetFile = SnippetUtils.getSnippetFile(snippetDir, task.getSnippetCode(), task.filename);
            if (snippetFile.isError) {
                return;
            }
            Checksum checksum;
            final String snippetCode = task.snippetCode;
            try {
                String checksumStr = Request.Get(snippetChecksumUrl+'/'+ snippetCode)
                        .connectTimeout(5000)
                        .socketTimeout(5000)
                        .execute().returnContent().asString(StandardCharsets.UTF_8);

                checksum = Checksum.fromJson(checksumStr);
            }
            catch (HttpResponseException e) {
                logError(snippetCode, e);
                break;
            }
            catch (SocketTimeoutException e) {
                log.error("SocketTimeoutException", e.toString());
                break;
            }
            catch (IOException e) {
                log.error("IOException", e);
                break;
            }

            try {
                File snippetTempFile = new File(snippetFile.file.getAbsolutePath()+".tmp");

                Request.Get(targetUrl+'/'+ snippetCode)
                        .connectTimeout(5000)
                        .socketTimeout(5000)
                        .execute().saveContent(snippetTempFile);

                CheckSumAndSignatureStatus status;
                try(FileInputStream fis = new FileInputStream(snippetTempFile)) {
                    status = checksumWithSignatureService.verifyChecksumAndSignature(checksum, snippetCode, fis, true);
                }
                if (globals.isAcceptOnlySignedSnippets && status.isSignatureOk==null) {
                    log.warn("globals.isAcceptOnlySignedSnippets is {} but snippet with code {} doesn't have signature", globals.isAcceptOnlySignedSnippets, snippetCode);
                    continue;
                }
                if (Boolean.FALSE.equals(status.isSignatureOk)) {
                    log.warn("globals.isAcceptOnlySignedSnippets is {} but snippet with code {} has the broken signature", globals.isAcceptOnlySignedSnippets, snippetCode);
                    continue;
                }
                if (status.isOk && !Boolean.FALSE.equals(status.isSignatureOk)) {
                    //noinspection ResultOfMethodCallIgnored
                    snippetTempFile.renameTo(snippetFile.file);
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
                log.error("SocketTimeoutException", e.toString());
            }
            catch (IOException e) {
                log.error("IOException", e);
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
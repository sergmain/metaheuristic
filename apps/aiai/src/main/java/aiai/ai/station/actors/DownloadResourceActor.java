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
import aiai.ai.Globals;
import aiai.ai.station.AssetFile;
import aiai.ai.utils.ResourceUtils;
import aiai.ai.station.StationTaskService;
import aiai.ai.station.net.HttpClientExecutor;
import aiai.ai.station.tasks.DownloadResourceTask;
import aiai.ai.utils.RestUtils;
import aiai.ai.yaml.station.StationTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.UUID;

@Service
@Slf4j
@Profile("station")
public class DownloadResourceActor extends AbstractTaskQueue<DownloadResourceTask> {

    private final Globals globals;
    private final StationTaskService stationTaskService;

    public DownloadResourceActor(Globals globals, StationTaskService stationTaskService) {
        this.globals = globals;
        this.stationTaskService = stationTaskService;
    }

    @PostConstruct
    public void postConstruct() {
    }

    public void fixedDelay() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }

        DownloadResourceTask task;
        while ((task = poll()) != null) {
            StationTask stationTask = stationTaskService.findById(task.launchpad.url, task.taskId);
            if (stationTask!=null && stationTask.finishedOn!=null) {
                log.info("Task #{} was already finished, skip it", task.taskId);
                continue;
            }
            AssetFile assetFile = ResourceUtils.prepareDataFile(task.targetDir, task.id, null);
            if (assetFile.isError ) {
                log.warn("Resource can't be downloaded. Asset file initialization was failed, {}", assetFile);
                continue;
            }
            if (assetFile.isContent ) {
                log.debug("Resource was already downloaded. Asset file: {}", assetFile);
                continue;
            }

            log.info("Start processing the download task {}", task);
            try {
                final String restUrl = task.launchpad.url + (task.launchpad.isSecureRestUrl ? Consts.REST_AUTH_URL : Consts.REST_ANON_URL );
                String payloadRestUrl = restUrl + '/' + UUID.randomUUID() + Consts.PAYLOAD_REST_URL + "/resource/DATA";

                Request request = Request.Post(payloadRestUrl + '/' + task.stationId+ '/' + task.getId())
                        .addHeader("X-Custom-header", "stuff")
                        .connectTimeout(10000)
                        .socketTimeout(10000);

                RestUtils.addHeaders(request);

                Response response;
                if (task.launchpad.isSecureRestUrl) {
                    response = HttpClientExecutor.getExecutor(task.launchpad.url, task.launchpad.restUsername, task.launchpad.restToken, task.launchpad.restPassword).execute(request);
                }
                else {
                    response = request.execute();
                }
                File parentDir = assetFile.file.getParentFile();
                if (parentDir==null) {
                    String es = "Can't get parent dir for asset file " + assetFile.file.getAbsolutePath();
                    log.error(es);
                    stationTaskService.finishAndWriteToLog(task.launchpad.url, task.taskId, es);
                    continue;
                }
                File tempFile;
                try {
                    tempFile = File.createTempFile("resource-", ".temp", parentDir);
                } catch (IOException e) {
                    String es = "Error creating temp file in parent dir: " + parentDir.getAbsolutePath();
                    log.error(es, e);
                    stationTaskService.finishAndWriteToLog(task.launchpad.url, task.taskId, es);
                    continue;
                }
                response.saveContent(tempFile);
                if (!tempFile.renameTo(assetFile.file)) {
                    log.warn("Can't rename file {} to file {}", tempFile.getPath(), assetFile.file.getPath());
                    continue;
                }
                log.info("Resource #{} was loaded", task.getId());
            } catch (HttpResponseException e) {
                if (e.getStatusCode() == HttpServletResponse.SC_GONE) {
                    log.warn("Resource with id {} wasn't found. stop processing task #{}", task.getId(), task.getTaskId());
                    stationTaskService.finishAndWriteToLog( task.launchpad.url, task.getTaskId(),
                            String.format("Resource %s wasn't found on launchpad. Task #%s is finished.", task.getId(), task.getTaskId() ));
                } else if (e.getStatusCode() == HttpServletResponse.SC_CONFLICT) {
                    log.warn("Resource with id {} is broken and need to be recreated", task.getId());
                } else {
                    log.error("HttpResponseException.getStatusCode(): {}", e.getStatusCode());
                    log.error("HttpResponseException", e);
                }
            } catch (SocketTimeoutException e) {
                log.error("SocketTimeoutException", e);
            } catch (IOException e) {
                log.error("IOException", e);
            }
        }
    }
}
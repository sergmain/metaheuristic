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
import aiai.ai.station.StationTaskService;
import aiai.ai.station.net.HttpClientExecutor;
import aiai.ai.station.tasks.DownloadResourceTask;
import aiai.ai.utils.ResourceUtils;
import aiai.ai.utils.RestUtils;
import aiai.ai.yaml.station.StationTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
                final String payloadRestUrl = restUrl + Consts.PAYLOAD_REST_URL + "/resource/" + Enums.BinaryDataType.DATA;
//                final String uri = payloadRestUrl + '/' + task.stationId + '/' + task.getId();
                final String uri = payloadRestUrl + '/' + UUID.randomUUID().toString().substring(0,8) + '-' + task.stationId+ '-' + task.taskId+ URLEncoder.encode(task.getId(), StandardCharsets.UTF_8.toString());

                final Request request = Request.Post(uri)
                        .bodyForm(Form.form()
                                .add("stationId", task.stationId)
                                .add("taskId", Long.toString(task.getTaskId()))
                                .add("code", task.getId())
                                .build(), StandardCharsets.UTF_8)
                        .connectTimeout(20000)
                        .socketTimeout(20000);


/*
                json = Request.Post(PRICE_PROXY_URL)
                        .bodyForm(Form.form().add("p", Const.RANDOM_PASS).add("j", j).build(), Const.UTF_8)
                        .execute().returnContent().asString(Const.UTF_8);

                String json = Request.Post("http://www.aaa.com/api/panel")
                        .bodyForm(Form.form()
                                .add("action", "item.summary")
                                .add("hash", "4ba62806109973643a8451e69bdc665a")
                                .add("gameid", "570")
                                .add("itemid", "6894312711")
                                .add("index", "4007")
                                .add("quality", "4")
                                .build(), UTF_8)
                        .execute();
*/

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
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
import aiai.ai.launchpad.server.UploadResult;
import aiai.ai.station.StationTaskService;
import aiai.ai.station.net.HttpClientExecutor;
import aiai.ai.station.tasks.UploadResourceTask;
import aiai.ai.yaml.station.StationTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@Profile("station")
public class UploadResourceActor extends AbstractTaskQueue<UploadResourceTask> {

    private static ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    private final Globals globals;
    private final StationTaskService stationTaskService;

    public UploadResourceActor(Globals globals, StationTaskService stationTaskService) {
        this.globals = globals;
        this.stationTaskService = stationTaskService;
    }

    @PostConstruct
    public void postConstruct() {
        if (globals.isStationEnabled) {
            //
        }
    }

    public static UploadResult fromJson(String json) {
        try {
            //noinspection UnnecessaryLocalVariable
            UploadResult result = mapper.readValue(json, UploadResult.class);
            return result;
        } catch (IOException e) {
            throw new RuntimeException("#311.77 error", e);
        }
    }

    public void fixedDelay() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }

        UploadResourceTask task;
        List<UploadResourceTask> repeat = new ArrayList<>();
        while((task = poll())!=null) {
            StationTask stationTask = stationTaskService.findById(task.launchpad.url, task.taskId);
            if (stationTask == null) {
                log.info("#311.71 task was already cleaned or didn't exist, {}, #{}", task.launchpad.url, task.taskId);
                continue;
            }
            if (stationTask.resourceUploaded) {
                log.info("#311.73 resource was already uploaded, {}, #{}", task.launchpad.url, task.taskId);
                continue;
            }
            Enums.UploadResourceStatus status = null;
            try {
                log.info("Start uploading result data to server, resultDataFile: {}", task.file);
                if (!task.file.exists()) {
                    log.error("#311.67 File {} doesn't exist", task.file.getPath());
                }

                final String restUrl = task.launchpad.url + (task.launchpad.isSecureRestUrl ? Consts.REST_AUTH_URL : Consts.REST_ANON_URL );
//                final String uploadRestUrl  = restUrl + '/' + UUID.randomUUID() + Consts.UPLOAD_REST_URL;
//                final String uri = uploadRestUrl + '/' + task.stationId+ '/' + task.taskId;
                final String uploadRestUrl  = restUrl + Consts.UPLOAD_REST_URL;
                final String uri = uploadRestUrl + '/' + UUID.randomUUID().toString().substring(0,8) + '-' + task.stationId+ '-' + task.taskId;

                HttpEntity entity = MultipartEntityBuilder.create()
                        .setMode(HttpMultipartMode.RFC6532)
                        .setCharset(StandardCharsets.UTF_8)
                        .addTextBody("stationId", task.stationId)
                        .addTextBody("taskId", Long.toString(task.taskId))
                        .addBinaryBody("file", task.file, ContentType.APPLICATION_OCTET_STREAM, task.file.getName())
                        .build();

                Request request = Request.Post(uri)
                        .connectTimeout(20000)
                        .socketTimeout(20000)
                        .body(entity);

                Response response;
                if (task.launchpad.isSecureRestUrl) {
                    response = HttpClientExecutor.getExecutor(task.launchpad.url, task.launchpad.restUsername, task.launchpad.restToken, task.launchpad.restPassword).execute(request);
                }
                else {
                    response = request.execute();
                }
                String json = response.returnContent().asString();

                UploadResult result = fromJson(json);
                if (result.status!= Enums.UploadResourceStatus.OK) {
                    log.error("#311.51 Error uploading file, server's error : " + result.error);
                }
                status = result.status;
            } catch (HttpResponseException e) {
                log.error("#311.55 Error uploading code", e);
            } catch (SocketTimeoutException e) {
                log.error("#311.58 SocketTimeoutException, {}", e.toString());
            }
            catch (IOException e) {
                log.error("#311.61 IOException", e);
            }
            catch (Throwable th) {
                log.error("#311.64 Throwable", th);
            }
            log.info("");
            if (status!=null) {
                switch(status) {
                    case OK:
                        log.info("Task was successfully uploaded to server, {}, {} ", task.launchpad.url, task.taskId);
                        stationTaskService.setResourceUploaded(task.launchpad.url, task.taskId);
                        break;
                    case FILENAME_IS_BLANK:
                    case TASK_WAS_RESET:
                    case TASK_NOT_FOUND:
                        log.error("#311.01 server return status {}", status);
                        break;
                    case PROBLEM_WITH_OPTIMISTIC_LOCKING:
                        log.warn("#311.05 problem with optimistic locking at server side, {}", status);
                        repeat.add(task);
                        break;
                    case GENERAL_ERROR:
                        log.warn("#311.07 general error at server side, {}", status);
                        repeat.add(task);
                        break;
                }
            }
            else {
                log.error("#311.09 Error accessing rest-server. Assign task one more time.");
                repeat.add(task);
            }
        }
        for (UploadResourceTask uploadResourceTask : repeat) {
            add(uploadResourceTask);
        }
    }
}
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
package ai.metaheuristic.ai.processor.actors;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.server.UploadResult;
import ai.metaheuristic.ai.processor.ProcessorTaskService;
import ai.metaheuristic.ai.processor.net.HttpClientExecutor;
import ai.metaheuristic.ai.processor.tasks.UploadResourceTask;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorTask;
import ai.metaheuristic.commons.CommonConsts;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor
public class UploadResourceActor extends AbstractTaskQueue<UploadResourceTask> {

    private static ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    private final Globals globals;
    private final ProcessorTaskService processorTaskService;

    private static UploadResult fromJson(String json) {
        try {
            //noinspection UnnecessaryLocalVariable
            UploadResult result = mapper.readValue(json, UploadResult.class);
            return result;
        } catch (IOException e) {
            throw new RuntimeException("#311.010 error", e);
        }
    }

    @SuppressWarnings("Duplicates")
    public void fixedDelay() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.processorEnabled) {
            return;
        }

        UploadResourceTask task;
        List<UploadResourceTask> repeat = new ArrayList<>();
        while((task = poll())!=null) {
            ProcessorTask processorTask = processorTaskService.findById(task.dispatcher.url, task.taskId);
            if (processorTask == null) {
                log.info("#311.020 task was already cleaned or didn't exist, {}, #{}", task.dispatcher.url, task.taskId);
                continue;
            }
            if (processorTask.resourceUploaded) {
                log.info("#311.030 resource was already uploaded, {}, #{}", task.dispatcher.url, task.taskId);
                continue;
            }
            log.info("Start uploading result data to server, resultDataFile: {}", task.file);
            if (!task.file.exists()) {
                log.error("#311.040 File {} doesn't exist", task.file.getPath());
                continue;
            }
            Enums.UploadResourceStatus status = null;
            try {
                final String uploadRestUrl  = task.dispatcher.url + CommonConsts.REST_V1_URL + Consts.UPLOAD_REST_URL;
                String randonPart = '/' + UUID.randomUUID().toString().substring(0, 8) + '-' + task.processorId + '-' + task.taskId;
                final String uri = uploadRestUrl + randonPart;

                HttpEntity entity = MultipartEntityBuilder.create()
                        .setMode(HttpMultipartMode.RFC6532)
                        .setCharset(StandardCharsets.UTF_8)
                        .addTextBody("processorId", task.processorId)
                        .addTextBody("taskId", Long.toString(task.taskId))
                        .addBinaryBody("file", task.file, ContentType.APPLICATION_OCTET_STREAM, task.file.getName())
                        .build();

                Request request = Request.Post(uri)
                        .connectTimeout(5000)
                        .socketTimeout(20000)
                        .body(entity);

                log.info("Start uploading resource to rest-server, {}", randonPart);
                Response response = HttpClientExecutor.getExecutor(task.dispatcher.url, task.dispatcher.restUsername, task.dispatcher.restPassword).execute(request);
                String json = response.returnContent().asString();
                UploadResult result = fromJson(json);
                log.info("Server response: {}", result);

                if (result.status!= Enums.UploadResourceStatus.OK) {
                    log.error("#311.050 Error uploading file, server's error : " + result.error);
                }
                status = result.status;

            } catch (HttpResponseException e) {
                log.error("#311.060 Error uploading resource to server, code: " + e.getStatusCode(), e);
            } catch (SocketTimeoutException e) {
                log.error("#311.070 SocketTimeoutException, {}", e.toString());
            }
            catch (IOException e) {
                log.error("#311.080 IOException", e);
            }
            catch (Throwable th) {
                log.error("#311.090 Throwable", th);
            }
            if (status!=null) {
                switch(status) {
                    case OK:
                        log.info("Resource was successfully uploaded to server, {}, {} ", task.dispatcher.url, task.taskId);
                        processorTaskService.setResourceUploadedAndCompleted(task.dispatcher.url, task.taskId);
                        break;
                    case FILENAME_IS_BLANK:
                    case TASK_WAS_RESET:
                    case TASK_NOT_FOUND:
                    case UNRECOVERABLE_ERROR:
                        processorTaskService.delete(task.dispatcher.url, task.taskId);
                        log.error("#311.100 server return status {}, this task will be deleted.", status);
                        break;
                    case PROBLEM_WITH_LOCKING:
                        log.warn("#311.110 problem with locking in DB at server side, {}", status);
                        repeat.add(task);
                        break;
                    case GENERAL_ERROR:
                        log.warn("#311.120 general error at server side, {}", status);
                        repeat.add(task);
                        break;
                }
            }
            else {
                log.error("#311.130 Error accessing rest-server. Assign task one more time.");
                repeat.add(task);
            }
        }
        for (UploadResourceTask uploadResourceTask : repeat) {
            add(uploadResourceTask);
        }
    }
}
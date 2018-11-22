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
import aiai.ai.launchpad.server.ServerService;
import aiai.ai.station.net.HttpClientExecutor;
import aiai.ai.station.tasks.UploadResourceTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class UploadResourceActor extends AbstractTaskQueue<UploadResourceTask> {

    private static ObjectMapper mapper;
    private final HttpClientExecutor executor;

    static {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    private final Globals globals;

    public UploadResourceActor(HttpClientExecutor executor, Globals globals) {
        this.executor = executor;
        this.globals = globals;
    }

    @PostConstruct
    public void postConstruct() {
        if (globals.isStationEnabled) {
            //
        }
    }

    public static ServerService.UploadResult fromJson(String json) {
        try {
            //noinspection UnnecessaryLocalVariable
            ServerService.UploadResult result = mapper.readValue(json, ServerService.UploadResult.class);
            return result;
        } catch (IOException e) {
            throw new RuntimeException("error", e);
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
            boolean isOk = false;
            try (InputStream is = new FileInputStream(task.file)) {
                log.info("Start uploading result data to server, resultDataFile: {}", task.file);

                final String uri = globals.uploadRestUrl + '/' + task.taskId;
                HttpEntity entity = MultipartEntityBuilder.create()
                        .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                        .setCharset(StandardCharsets.UTF_8)
                        .addBinaryBody("file", is, ContentType.MULTIPART_FORM_DATA, task.file.getName())
                        .build();

                Request request = Request.Post(uri)
                        .connectTimeout(5000)
                        .socketTimeout(5000)
                        .body(entity);

                Response response;
                if (globals.isSecureRestUrl) {
                    response = executor.executor.execute(request);
                }
                else {
                    response = request.execute();
                }
                String json = response.returnContent().asString();

                ServerService.UploadResult result = fromJson(json);
                log.info("'\tresult data was successfully uploaded");
                if (!result.isOk) {
                    log.error("Error uploading file, server error: " + result.error);
                }
                isOk = result.isOk;
            } catch (HttpResponseException e) {
                log.error("Error uploading code", e);
            } catch (SocketTimeoutException e) {
                log.error("SocketTimeoutException", e.toString());
            }
            catch (IOException e) {
                log.error("IOException", e);
            }
            catch (Throwable th) {
                log.error("Throwable", th);
            }
            if (!isOk) {
                log.error("'\tTask assigned one more time.");
                repeat.add(task);
            }

        }
        for (UploadResourceTask uploadResourceTask : repeat) {
            add(uploadResourceTask);
        }
    }
}
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
import aiai.ai.station.tasks.UploadResourceTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class UploadResourceActor extends AbstractTaskQueue<UploadResourceTask> {

    private static ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    private final Globals globals;

    public UploadResourceActor(Globals globals) {
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
        while((task = poll())!=null) {
            try (InputStream is = new FileInputStream(task.file)) {
                HttpEntity entity = MultipartEntityBuilder.create()
                        .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                        .setCharset(StandardCharsets.UTF_8)
                        .addBinaryBody("file", is, ContentType.MULTIPART_FORM_DATA, task.file.getName())
                        .build();
                String json = Request.Post(globals.uploadRestUrl + '/' + task.code)
                        .connectTimeout(5000)
                        .socketTimeout(5000)
                        .body(entity)
                        .execute().returnContent().asString();

                ServerService.UploadResult result = fromJson(json);
                if (!result.isOk) {
                    log.error("Error uploading file, text: " + result.error);
                    add(task);
                }
            } catch (HttpResponseException e) {
                log.error("Error uploading code", e);
                break;
            } catch (SocketTimeoutException e) {
                log.error("SocketTimeoutException", e.toString());
                break;
            } catch (IOException e) {
                log.error("IOException", e);
                break;
            }
        }
    }
}
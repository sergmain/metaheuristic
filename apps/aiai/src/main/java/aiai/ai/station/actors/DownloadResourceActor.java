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
import aiai.ai.station.AssetFile;
import aiai.ai.station.StationResourceUtils;
import aiai.ai.station.net.HttpClientExecutor;
import aiai.ai.station.tasks.DownloadResourceTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class DownloadResourceActor extends AbstractTaskQueue<DownloadResourceTask> {

    private final Globals globals;
    private final HttpClientExecutor executor;

    private final Map<String, Boolean> preparedMap = new LinkedHashMap<>();
    private String targetUrl;

    public DownloadResourceActor(Globals globals, HttpClientExecutor executor) {
        this.globals = globals;
        this.executor = executor;
    }

    @PostConstruct
    public void postConstruct() {
        if (globals.isStationEnabled) {
            targetUrl = globals.payloadRestUrl + "/resource";
        }
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
            if (Boolean.TRUE.equals(preparedMap.get(task.getId()))) {
                continue;
            }
            AssetFile assetFile = StationResourceUtils.prepareResourceFile(globals.stationResourcesDir, task.binaryDataType, task.id);
            if (assetFile.isError ) {
                log.warn("Resource can't be downloaded. Asset file initialization was failed, {}", assetFile);
                continue;
            }

            try {
                Request request = Request.Get(targetUrl + '/' + task.getBinaryDataType() + '/' + task.getId())
                        .connectTimeout(5000)
                        .socketTimeout(5000);

                Response response;
                if (globals.isSecureRestUrl) {
                    response = executor.executor.execute(request);
                }
                else {
                    response = request.execute();
                }
                response.saveContent(assetFile.file);

                preparedMap.put(task.getId(), true);
                log.info("Resource #{} was loaded", task.getId());
            } catch (HttpResponseException e) {
                if (e.getStatusCode() == HttpServletResponse.SC_GONE) {
                    log.warn("Resource with id {} wasn't found", task.getId());
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
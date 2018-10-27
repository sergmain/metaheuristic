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
import aiai.ai.station.StationDatasetUtils;
import aiai.ai.station.tasks.DownloadResourceTask;
import aiai.ai.utils.DigitUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Request;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class DownloadResourceActor extends AbstractTaskQueue<DownloadResourceTask> {

    private final Globals globals;
    private final Map<Long, Boolean> preparedMap = new LinkedHashMap<>();
    private String targetUrl;

    public DownloadResourceActor(Globals globals) {
        this.globals = globals;
    }

    @PostConstruct
    public void postConstruct() {
        if (globals.isStationEnabled) {
            targetUrl = globals.launchpadUrl + "/payload/resource";
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
//            AssetFile assetFile = StationDatasetUtils.prepareDatasetFile(stationDir, task.getId());
            DigitUtils.Power power = DigitUtils.getPower(task.getId());
            File typeDir = new File(globals.stationResourcesDir, task.getType().toString());
            File trgDir = new File(typeDir,
                    ""+power.power7+File.separatorChar+power.power4+File.separatorChar);
            trgDir.mkdirs();
            File file = new File(trgDir, ""+task.getId()+".bin");

//            if (assetFile.isError) {
//                log.warn("Problem with asset file {}", assetFile);
//                return;
//            }
            try {
                Request.Get(targetUrl + '/' + task.getType() + '/' + task.getId())
                        .connectTimeout(5000)
                        .socketTimeout(5000)
                        .execute().saveContent(file);
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
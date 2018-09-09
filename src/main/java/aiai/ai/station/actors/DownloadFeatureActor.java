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
import aiai.ai.station.StationDatasetUtils;
import aiai.ai.station.StationFeatureUtils;
import aiai.ai.station.tasks.DownloadFeatureTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.fluent.Request;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@EnableScheduling
@Slf4j
public class DownloadFeatureActor extends AbstractTaskQueue<DownloadFeatureTask> {

    private final Globals globals;
    private final Map<Long, Boolean> preparedMap = new LinkedHashMap<>();
    private String targetUrl;

    public DownloadFeatureActor(Globals globals) {
        this.globals = globals;
    }

    @PostConstruct
    public void postConstruct() {
        if (globals.isStationEnabled) {
            targetUrl = globals.launchpadUrl + "/payload/feature";
        }
    }

    @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.download-feature-task.timeout'), 3, 20, 10)*1000 }")
    public void fixedDelay() {
        log.info("DownloadSnippetActor.fixedDelay()");
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }

        File stationDir = StationDatasetUtils.checkAndCreateDatasetDir(globals.stationDir);
        if (stationDir == null) {
            return;
        }

        DownloadFeatureTask task;
        while ((task = poll()) != null) {
            if (Boolean.TRUE.equals(preparedMap.get(task.getFeatureId()))) {
                continue;
            }
            AssetFile assetFile = StationFeatureUtils.prepareFeatureFile(stationDir, task.datasetId, task.featureId);
            if (assetFile.isError) {
                return;
            }
            try {
                Request.Get(targetUrl + '/' + task.getFeatureId()).execute().saveContent(assetFile.file);
            }
            catch (IOException e) {
                e.printStackTrace();
            }

            preparedMap.put(task.getDatasetId(), true);
        }
    }

}
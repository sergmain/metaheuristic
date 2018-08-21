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

import aiai.ai.station.StationDatasetUtils;
import aiai.ai.station.tasks.DownloadDatasetTask;
import org.apache.http.client.fluent.Request;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@EnableScheduling
public class DownloadDatasetActor extends AbstractTaskQueue<DownloadDatasetTask> {

    @Value("#{ T(aiai.ai.utils.EnvProperty).toFile( environment.getProperty('aiai.station.dir' )) }")
    private File stationDir;

    @Value("#{ T(aiai.ai.station.actors.DownloadDatasetActor).fullUrl( environment.getProperty('aiai.station.launchpad.url' )) }")
    private String targetUrl;

    private final Map<Long, Boolean> preparedMap = new LinkedHashMap<>();

    public static String fullUrl(String srvUrl) {
        return srvUrl + "/payload/dataset";
    }

    @Scheduled(fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.download-dataset-task.timeout'), 3, 20, 10)*1000 }")
    public void fixedDelayTaskComplex() {

        File dsDir = StationDatasetUtils.checkEvironment(stationDir);
        if (dsDir==null) {
            return;
        }

        DownloadDatasetTask task;
        while((task = poll())!=null) {
            if (Boolean.TRUE.equals(preparedMap.get(task.getDatasetId()))) {
                continue;
            }
            StationDatasetUtils.DatasetFile datasetFile = StationDatasetUtils.getDatasetFile(dsDir, task.datasetId);
            if (datasetFile.isError) {
                return;
            }
            try {
                Request.Get(targetUrl+'/'+task.getDatasetId()).execute().saveContent(datasetFile.file);
            } catch (IOException e) {
                e.printStackTrace();
            }

            preparedMap.put(task.getDatasetId(), true);
        }
    }

}
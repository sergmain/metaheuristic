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

    @Scheduled(fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.download-dataset-task.timeout'), 3, 20, 5) }")
    public void fixedDelayTaskComplex() {

        File dsDir = checkEvironment();
        if (dsDir==null) {
            System.out.println("Station enviroment is broken. See log for more information");
            return;
        }
        
        DownloadDatasetTask task;
        while((task = poll())!=null) {
            if (preparedMap.get(task.getDatasetId())) {
                continue;
            }

            File currDir = new File(dsDir, String.format("%03d", task.getDatasetId()));
            boolean isOk = currDir.mkdirs();
            if (!isOk) {
                System.out.println("Can't make all directories for path: " + currDir.getAbsolutePath());
                return;
            }

            File currFile = new File(currDir, String.format("dataset-%03d", task.getDatasetId()));
            try {
                Request.Get(targetUrl+'/'+task.getDatasetId()).execute().saveContent(currFile);
            } catch (IOException e) {
                e.printStackTrace();
            }

            preparedMap.put(task.getDatasetId(), true);
        }
    }

    private File checkEvironment() {
        File dsDir = new File(stationDir, "dataset");
        if (!dsDir.exists()) {
            boolean isOk = dsDir.mkdirs();
            if (!isOk) {
                System.out.println("Can't make all directories for path: " + dsDir.getAbsolutePath());
                return null;
            }
        }
        return dsDir;
    }
}
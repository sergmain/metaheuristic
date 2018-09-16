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
import aiai.ai.station.StationSnippetUtils;
import aiai.ai.station.tasks.DownloadSnippetTask;
import aiai.ai.utils.DirUtils;
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
@Slf4j
public class DownloadSnippetActor extends AbstractTaskQueue<DownloadSnippetTask> {

    private final Globals globals;

    private String targetUrl;

    private final Map<String, Boolean> preparedMap = new LinkedHashMap<>();

    public DownloadSnippetActor(Globals globals) {
        this.globals = globals;
    }

    @PostConstruct
    public void postConstruct() {
        if (globals.isStationEnabled) {
            targetUrl = globals.launchpadUrl + "/payload/snippet";
        }
    }

    public void fixedDelay() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }

        File snippetDir = DirUtils.createDir(globals.stationDir, "snippet");
        if (snippetDir==null) {
            System.out.println("Station enviroment is broken. See log for more information");
            return;
        }

        DownloadSnippetTask task;
        while((task = poll())!=null) {
            if (Boolean.TRUE.equals(preparedMap.get(task.getSnippetCode()))) {
                continue;
            }

            StationSnippetUtils.SnippetFile snippetFile = StationSnippetUtils.getSnippetFile(snippetDir, task.getSnippetCode(), task.filename);
            if (snippetFile.isError) {
                return;
            }
            try {
                Request.Get(targetUrl+'/'+task.snippetCode)
                        .connectTimeout(5000)
                        .socketTimeout(5000)
                        .execute().saveContent(snippetFile.file);
                preparedMap.put(task.snippetCode, true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
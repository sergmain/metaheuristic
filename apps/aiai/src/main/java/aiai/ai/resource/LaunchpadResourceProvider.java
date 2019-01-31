/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.resource;

import aiai.ai.Consts;
import aiai.ai.core.ExecProcessService;
import aiai.ai.station.LaunchpadLookupExtendedService;
import aiai.ai.station.actors.DownloadResourceActor;
import aiai.ai.station.actors.UploadResourceActor;
import aiai.ai.station.tasks.DownloadResourceTask;
import aiai.ai.station.tasks.UploadResourceTask;
import aiai.ai.yaml.metadata.Metadata;
import aiai.ai.yaml.station.StationTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class LaunchpadResourceProvider implements ResourceProvider {

    private final DownloadResourceActor downloadResourceActor;
    private final UploadResourceActor uploadResourceActor;

    public LaunchpadResourceProvider(DownloadResourceActor downloadResourceActor, UploadResourceActor uploadResourceActor) {
        this.downloadResourceActor = downloadResourceActor;
        this.uploadResourceActor = uploadResourceActor;
    }

    @Override
    public List<AssetFile> prepareDataFile(
            File taskDir, LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad,
            StationTask task, Metadata.LaunchpadInfo launchpadCode,
            String resourceCode, String storageUrl) {

        DownloadResourceTask resourceTask = new DownloadResourceTask(resourceCode, task.getTaskId(), taskDir);
        resourceTask.launchpad = launchpad.launchpadLookup;
        resourceTask.stationId = launchpadCode.stationId;
        downloadResourceActor.add(resourceTask);

        AssetFile assetFile = ResourceUtils.prepareDataFile(taskDir, resourceCode, null);
        return Collections.singletonList(assetFile);
    }

    @Override
    public ExecProcessService.Result processResultingFile(
            LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad,
            StationTask task, Metadata.LaunchpadInfo launchpadCode,
            File outputResourceFile) {
        if (outputResourceFile.exists()) {
            log.info("Register task for uploading result data to server, resultDataFile: {}", outputResourceFile.getPath());
            UploadResourceTask uploadResourceTask = new UploadResourceTask(task.taskId, outputResourceFile);
            uploadResourceTask.launchpad = launchpad.launchpadLookup;
            uploadResourceTask.stationId = launchpadCode.stationId;
            uploadResourceActor.add(uploadResourceTask);
        } else {
            String es = "Result data file doesn't exist, resultDataFile: " + outputResourceFile.getPath();
            log.error(es);
            return new ExecProcessService.Result(false, -1, es);
        }
        return null;
    }

    @Override
    public File getOutputResourceFile(
            File taskDir, LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad,
            StationTask task, String outputResourceCode, String storageUrl) {

        //noinspection UnnecessaryLocalVariable
        File resultDataFile = new File(taskDir, Consts.ARTIFACTS_DIR + File.separatorChar + outputResourceCode);
        return resultDataFile;
    }

}

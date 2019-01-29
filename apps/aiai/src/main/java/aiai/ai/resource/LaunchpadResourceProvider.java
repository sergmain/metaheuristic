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

import aiai.ai.station.LaunchpadLookupExtendedService;
import aiai.ai.station.actors.DownloadResourceActor;
import aiai.ai.station.tasks.DownloadResourceTask;
import aiai.ai.yaml.metadata.Metadata;
import aiai.ai.yaml.station.StationTask;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Collections;
import java.util.List;

@Service
public class LaunchpadResourceProvider implements ResourceProvider {

    private final DownloadResourceActor downloadResourceActor;

    public LaunchpadResourceProvider(DownloadResourceActor downloadResourceActor) {
        this.downloadResourceActor = downloadResourceActor;
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
}

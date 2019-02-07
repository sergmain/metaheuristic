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

package aiai.ai.station.station_resource;

import aiai.ai.core.ExecProcessService;
import aiai.ai.resource.AssetFile;
import aiai.ai.station.LaunchpadLookupExtendedService;
import aiai.ai.yaml.metadata.Metadata;
import aiai.ai.yaml.station.StationTask;

import java.io.File;
import java.util.List;

public interface ResourceProvider {
    List<AssetFile> prepareForDownloadingDataFile(File taskDir, LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad, StationTask task, Metadata.LaunchpadInfo launchpadCode, String resourceCode, String storageUrl);

    ExecProcessService.Result processResultingFile(
            LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad,
            StationTask task, Metadata.LaunchpadInfo launchpadCode,
            File outputResourceFile
    );

    File getOutputResourceFile(
            File taskDir,
            LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad,
            StationTask task,
            String outputResourceCode, String storageUrl);
}

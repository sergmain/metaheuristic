/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.ai.station.station_resource;

import ai.metaheuristic.ai.resource.AssetFile;
import ai.metaheuristic.ai.station.LaunchpadLookupExtendedService;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.station_task.StationTask;
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data_storage.DataStorageParams;

import java.io.File;
import java.util.List;

public interface ResourceProvider {
    List<AssetFile> prepareForDownloadingDataFile(File taskDir, LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad, StationTask task, Metadata.LaunchpadInfo launchpadCode, String resourceId, DataStorageParams dataStorageParams);

    SnippetApiData.SnippetExecResult processResultingFile(
            LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad,
            StationTask task, Metadata.LaunchpadInfo launchpadCode,
            File outputResourceFile,
            TaskParamsYaml.SnippetConfig snippet
    );

    File getOutputResourceFile(
            File taskDir,
            LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad,
            StationTask task,
            String outputResourceCode, DataStorageParams dataStorageParams);
}

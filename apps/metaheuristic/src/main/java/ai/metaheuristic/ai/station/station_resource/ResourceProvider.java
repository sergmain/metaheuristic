/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
import ai.metaheuristic.ai.station.DispatcherLookupExtendedService;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.station_task.StationTask;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;

import java.io.File;
import java.util.List;

public interface ResourceProvider {
    List<AssetFile> prepareForDownloadingDataFile(
            File taskDir, DispatcherLookupExtendedService.LaunchpadLookupExtended launchpad,
            StationTask task, Metadata.DispatcherInfo launchpadCode,
            String resourceId, SourceCodeParamsYaml.Variable dataStorageParams);

    FunctionApiData.SystemExecResult processResultingFile(
            DispatcherLookupExtendedService.LaunchpadLookupExtended launchpad,
            StationTask task, Metadata.DispatcherInfo launchpadCode,
            String outputResourceId,
            TaskParamsYaml.FunctionConfig functionConfig
    );

    File getOutputResourceFile(
            File taskDir,
            DispatcherLookupExtendedService.LaunchpadLookupExtended launchpad,
            StationTask task,
            String outputResourceId, SourceCodeParamsYaml.Variable dataStorageParams);
}

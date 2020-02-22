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
import ai.metaheuristic.ai.resource.ResourceUtils;
import ai.metaheuristic.ai.station.DispatcherLookupExtendedService;
import ai.metaheuristic.ai.station.actors.DownloadResourceActor;
import ai.metaheuristic.ai.station.actors.UploadResourceActor;
import ai.metaheuristic.ai.station.tasks.DownloadResourceTask;
import ai.metaheuristic.ai.station.tasks.UploadResourceTask;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.station_task.StationTask;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@Profile("station")
@RequiredArgsConstructor
public class DispatcherResourceProvider implements ResourceProvider {

    private final DownloadResourceActor downloadResourceActor;
    private final UploadResourceActor uploadResourceActor;

    @Override
    public List<AssetFile> prepareForDownloadingDataFile(
            File taskDir, DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher,
            StationTask task, Metadata.DispatcherInfo dispatcherCode,
            String resourceId, SourceCodeParamsYaml.Variable dataStorageParams) {

        // process it only if the dispatcher has already sent its config
        if (dispatcher.context.chunkSize != null) {
            DownloadResourceTask resourceTask = new DownloadResourceTask(resourceId, task.getTaskId(), taskDir, dispatcher.context.chunkSize);
            resourceTask.dispatcher = dispatcher.dispatcherLookup;
            resourceTask.stationId = dispatcherCode.stationId;
            downloadResourceActor.add(resourceTask);
        }
        return Collections.singletonList(ResourceUtils.prepareDataFile(taskDir, resourceId, null));
    }

    @Override
    public FunctionApiData.SystemExecResult processResultingFile(
            DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher,
            StationTask task, Metadata.DispatcherInfo dispatcherCode,
            String outputResourceId, TaskParamsYaml.FunctionConfig functionConfig) {
        File outputResourceFile = Path.of(ConstsApi.ARTIFACTS_DIR, outputResourceId).toFile();
        if (outputResourceFile.exists()) {
            log.info("Register task for uploading result data to server, resultDataFile: {}", outputResourceFile.getPath());
            UploadResourceTask uploadResourceTask = new UploadResourceTask(task.taskId, outputResourceFile);
            uploadResourceTask.dispatcher = dispatcher.dispatcherLookup;
            uploadResourceTask.stationId = dispatcherCode.stationId;
            uploadResourceActor.add(uploadResourceTask);
        } else {
            String es = "Result data file doesn't exist, resultDataFile: " + outputResourceFile.getPath();
            log.error(es);
            return new FunctionApiData.SystemExecResult(functionConfig.code,false, -1, es);
        }
        return null;
    }

    @Override
    public File getOutputResourceFile(
            File taskDir, DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher,
            StationTask task, String outputResourceCode, SourceCodeParamsYaml.Variable dataStorageParams) {

        //noinspection UnnecessaryLocalVariable
        File resultDataFile = new File(taskDir, ConstsApi.ARTIFACTS_DIR + File.separatorChar + outputResourceCode);
        return resultDataFile;
    }

}

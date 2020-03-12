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

package ai.metaheuristic.ai.processor.processor_resource;

import ai.metaheuristic.ai.processor.DispatcherLookupExtendedService;
import ai.metaheuristic.ai.processor.actors.DownloadResourceActor;
import ai.metaheuristic.ai.processor.actors.UploadResourceActor;
import ai.metaheuristic.ai.processor.tasks.DownloadResourceTask;
import ai.metaheuristic.ai.processor.tasks.UploadVariableTask;
import ai.metaheuristic.ai.resource.AssetFile;
import ai.metaheuristic.ai.resource.ResourceUtils;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorTask;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.data.FunctionApiData;
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
@Profile("processor")
@RequiredArgsConstructor
public class DispatcherResourceProvider implements VariableProvider {

    private final DownloadResourceActor downloadResourceActor;
    private final UploadResourceActor uploadResourceActor;

    @Override
    public List<AssetFile> prepareForDownloadingVariable(
            File taskDir, DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher,
            ProcessorTask task, Metadata.DispatcherInfo dispatcherCode,
            TaskParamsYaml.InputVariable variable) {

        // process it only if the dispatcher has already sent its config
        if (dispatcher.context.chunkSize != null) {
            DownloadResourceTask resourceTask = new DownloadResourceTask(variable.id, task.getTaskId(), taskDir, dispatcher.context.chunkSize);
            resourceTask.dispatcher = dispatcher.dispatcherLookup;
            resourceTask.processorId = dispatcherCode.processorId;
            downloadResourceActor.add(resourceTask);
        }
        return Collections.singletonList(ResourceUtils.prepareFileForVariable(taskDir, variable.id, null));
    }

    @Override
    public FunctionApiData.SystemExecResult processOutputVariable(
            DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher,
            ProcessorTask task, Metadata.DispatcherInfo dispatcherCode,
            String outputVariableId, TaskParamsYaml.FunctionConfig functionConfig) {
        File outputResourceFile = Path.of(ConstsApi.ARTIFACTS_DIR, outputVariableId).toFile();
        if (outputResourceFile.exists()) {
            log.info("Register task for uploading result data to server, resultDataFile: {}", outputResourceFile.getPath());
            UploadVariableTask uploadResourceTask = new UploadVariableTask(task.taskId, outputResourceFile, outputVariableId);
            uploadResourceTask.dispatcher = dispatcher.dispatcherLookup;
            uploadResourceTask.processorId = dispatcherCode.processorId;
            uploadResourceActor.add(uploadResourceTask);
        } else {
            String es = "Result data file doesn't exist, resultDataFile: " + outputResourceFile.getPath();
            log.error(es);
            return new FunctionApiData.SystemExecResult(functionConfig.code,false, -1, es);
        }
        return null;
    }

    @Override
    public File getOutputVariableFromFile(
            File taskDir, DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher,
            ProcessorTask task, TaskParamsYaml.OutputVariable variable) {

        //noinspection UnnecessaryLocalVariable
        File resultDataFile = new File(taskDir, ConstsApi.ARTIFACTS_DIR + File.separatorChar + variable.id);
        return resultDataFile;
    }

}

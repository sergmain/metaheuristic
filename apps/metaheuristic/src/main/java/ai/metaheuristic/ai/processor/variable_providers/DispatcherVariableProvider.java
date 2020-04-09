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

package ai.metaheuristic.ai.processor.variable_providers;

import ai.metaheuristic.ai.exceptions.BreakFromLambdaException;
import ai.metaheuristic.ai.processor.DispatcherLookupExtendedService;
import ai.metaheuristic.ai.processor.actors.DownloadVariableService;
import ai.metaheuristic.ai.processor.actors.UploadVariableService;
import ai.metaheuristic.ai.processor.tasks.DownloadVariableTask;
import ai.metaheuristic.ai.processor.tasks.UploadVariableTask;
import ai.metaheuristic.ai.utils.asset.AssetFile;
import ai.metaheuristic.ai.utils.asset.AssetUtils;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorTask;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
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
public class DispatcherVariableProvider implements VariableProvider {

    private final DownloadVariableService downloadVariableService;
    private final UploadVariableService uploadVariableService;

    @Override
    public EnumsApi.DataSourcing getSourcing() {
        return EnumsApi.DataSourcing.dispatcher;
    }

    @Override
    public List<AssetFile> prepareForDownloadingVariable(
            File taskDir, DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher,
            ProcessorTask task, Metadata.DispatcherInfo dispatcherCode,
            TaskParamsYaml.InputVariable variable) {

        // process it only if the dispatcher has already sent its config
        if (dispatcher.context.chunkSize != null) {
            DownloadVariableTask variableTask = new DownloadVariableTask(variable.id, variable.context, task.getTaskId(), taskDir, dispatcher.context.chunkSize);
            variableTask.dispatcher = dispatcher.dispatcherLookup;
            variableTask.processorId = dispatcherCode.processorId;
            downloadVariableService.add(variableTask);
        }
        EnumsApi.DataType type;
        String es;
        switch(variable.context) {
            case global:
                type = EnumsApi.DataType.global_variable;
                break;
            case local:
                type = EnumsApi.DataType.variable;
                break;
            case array:
                es = "#810.005 Array type of variable isn't supported right now, variableId: " + variable.id;
                log.error(es);
                throw new BreakFromLambdaException(es);
            default:
                es = "#810.007 Unknown context: " + variable.context+ ", variableId: " +  variable.id;
                log.error(es);
                throw new BreakFromLambdaException(es);
        }
        return Collections.singletonList(AssetUtils.prepareFileForVariable(taskDir, variable.id, null, type));
    }

    @Override
    public FunctionApiData.SystemExecResult processOutputVariable(
            DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher,
            ProcessorTask task, Metadata.DispatcherInfo dispatcherCode,
            String outputVariableId, TaskParamsYaml.FunctionConfig functionConfig) {
        File outputVariableFile = Path.of(ConstsApi.ARTIFACTS_DIR, outputVariableId).toFile();
        if (outputVariableFile.exists()) {
            log.info("Register task for uploading result data to server, resultDataFile: {}", outputVariableFile.getPath());
            UploadVariableTask uploadVariableTask = new UploadVariableTask(task.taskId, outputVariableFile, outputVariableId);
            uploadVariableTask.dispatcher = dispatcher.dispatcherLookup;
            uploadVariableTask.processorId = dispatcherCode.processorId;
            uploadVariableService.add(uploadVariableTask);
        } else {
            String es = "Result data file doesn't exist, resultDataFile: " + outputVariableFile.getPath();
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

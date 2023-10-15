/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
import ai.metaheuristic.ai.processor.actors.DownloadVariableService;
import ai.metaheuristic.ai.processor.actors.UploadVariableService;
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.processor.tasks.DownloadVariableTask;
import ai.metaheuristic.ai.processor.tasks.UploadVariableTask;
import ai.metaheuristic.ai.utils.asset.AssetFile;
import ai.metaheuristic.ai.utils.asset.AssetUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupExtendedParams;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorCoreTask;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.exceptions.WrongVersionOfParamsException;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static ai.metaheuristic.api.EnumsApi.DataSourcing;
import static ai.metaheuristic.api.EnumsApi.DataType;

@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class DispatcherVariableProvider implements VariableProvider {

    private final DownloadVariableService downloadVariableService;
    private final UploadVariableService uploadVariableService;

    @Override
    public DataSourcing getSourcing() {
        return DataSourcing.dispatcher;
    }

    @Override
    public List<AssetFile> prepareForDownloadingVariable(
            ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Path taskDir, DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher,
            ProcessorCoreTask task, MetadataParamsYaml.ProcessorSession processorState,
            TaskParamsYaml.InputVariable variable) {

        try {

            if (variable.context==EnumsApi.VariableContext.array) {
                createDownloadTasksForArray(core, variable.id, task.getTaskId(), taskDir,
                        dispatcher.dispatcherLookup, variable.getNullable());
            }
            else {
                DownloadVariableTask variableTask = new DownloadVariableTask(
                        core, variable.id, variable.context, task.getTaskId(), taskDir,
                        dispatcher.dispatcherLookup, variable.getNullable());
                downloadVariableService.add(variableTask);
            }
            String es;
            switch(variable.context) {
                case global:
                    return List.of(AssetUtils.prepareFileForVariable(taskDir, variable.id.toString(), null, DataType.global_variable));
                case local:
                    return List.of(AssetUtils.prepareFileForVariable(taskDir, variable.id.toString(), null, DataType.variable));
                case array:
                    return prepareArrayVariable(taskDir, variable);
                default:
                    es = "#810.007 Unknown context: " + variable.context+ ", variableId: " +  variable.id;
                    log.error(es);
                    throw new BreakFromLambdaException(es);
            }
        }
        catch (WrongVersionOfParamsException e) {
            String es = "#810.010 Error while processing task #"+task.getTaskId()+", variable #" + variable.id;
            log.error(es, e);
            throw new BreakFromLambdaException(e);
        }
        catch (IOException e) {
            throw new BreakFromLambdaException(e);
        }
    }

    private void createDownloadTasksForArray(
            ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core,
            Long variableId, Long taskId, Path taskDir, DispatcherLookupParamsYaml.DispatcherLookup dispatcherLookup,
            boolean nullable) throws IOException {
        DownloadVariableTask task = new DownloadVariableTask(
                core, variableId, EnumsApi.VariableContext.local, taskId, taskDir, dispatcherLookup, nullable);
        downloadVariableService.add(task);

        AssetFile assetFile = AssetUtils.prepareFileForVariable(taskDir, variableId.toString(), null, DataType.variable);

        List<VariableArrayParamsYaml.Variable> variables = getVariablesForArray(assetFile);

        for (VariableArrayParamsYaml.Variable v : variables) {
            // element of array of variables can't be nullable
            DownloadVariableTask task1 = new DownloadVariableTask(
                    core, v.id, v.dataType==DataType.variable ? EnumsApi.VariableContext.local : EnumsApi.VariableContext.global,
                    taskId, taskDir, dispatcherLookup, false);
            downloadVariableService.add(task1);
        }
    }

    private static List<AssetFile> prepareArrayVariable(Path taskDir, TaskParamsYaml.InputVariable variable) throws IOException {
        AssetFile assetFile = AssetUtils.prepareFileForVariable(taskDir, variable.id.toString(), null, DataType.variable);
        List<AssetFile> assetFiles = new ArrayList<>();
        assetFiles.add(assetFile);
        List<VariableArrayParamsYaml.Variable> variables = getVariablesForArray(assetFile);

        for (VariableArrayParamsYaml.Variable v : variables) {
            AssetFile af = AssetUtils.prepareFileForVariable(taskDir, v.id, null, v.dataType);
            assetFiles.add(af);
        }
        return assetFiles;
    }

    private static List<VariableArrayParamsYaml.Variable> getVariablesForArray(AssetFile assetFile) throws IOException {
        List<VariableArrayParamsYaml.Variable> variables = new ArrayList<>();
        if (assetFile.isContent && !assetFile.isError) {
            String data = Files.readString(assetFile.file, StandardCharsets.UTF_8);
            VariableArrayParamsYaml vapy = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.to(data);
            variables.addAll(vapy.array);
        }
        return variables;
    }

    @Override
    @Nullable
    public FunctionApiData.SystemExecResult processOutputVariable(
            ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Path taskDir, DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher,
            ProcessorCoreTask task, MetadataParamsYaml.ProcessorSession processorState,
            TaskParamsYaml.OutputVariable outputVariable, TaskParamsYaml.FunctionConfig functionConfig) {
        Path outputVariableFile = taskDir.resolve(ConstsApi.ARTIFACTS_DIR).resolve(outputVariable.id.toString());
        if (Files.exists(outputVariableFile)) {
            log.info("Register task for uploading result data to server, resultDataFile: {}", outputVariableFile.toAbsolutePath());
            UploadVariableTask uploadVariableTask = new UploadVariableTask(task.taskId, outputVariableFile, outputVariable.id, core, dispatcher.dispatcherLookup);
            uploadVariableService.add(uploadVariableTask);
            return null;
        }
        else if (Boolean.TRUE.equals(outputVariable.getNullable())) {
            log.info("Register an upload task for setting a variable #{} as null", outputVariable.id);
            UploadVariableTask uploadVariableTask = new UploadVariableTask(task.taskId, outputVariable.id, true, core, dispatcher.dispatcherLookup);
            uploadVariableService.add(uploadVariableTask);
            return null;
        }
        else {
            String es = "Result data file doesn't exist, resultDataFile: " + outputVariableFile.toAbsolutePath();
            log.error(es);
            return new FunctionApiData.SystemExecResult(functionConfig.code,false, -1, es);
        }
    }

    @Override
    public Path getOutputVariableFromFile(
            ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Path taskDir, DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher,
            ProcessorCoreTask task, TaskParamsYaml.OutputVariable variable) {

        Path resultDataFile = taskDir.resolve(ConstsApi.ARTIFACTS_DIR).resolve(variable.id.toString());
        return resultDataFile;
    }
}

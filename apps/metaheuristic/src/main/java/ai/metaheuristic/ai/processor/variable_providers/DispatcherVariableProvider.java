/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.processor.tasks.DownloadVariableTask;
import ai.metaheuristic.ai.processor.tasks.UploadVariableTask;
import ai.metaheuristic.ai.utils.asset.AssetFile;
import ai.metaheuristic.ai.utils.asset.AssetUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorTask;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.exceptions.WrongVersionOfParamsException;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static ai.metaheuristic.api.EnumsApi.DataSourcing;
import static ai.metaheuristic.api.EnumsApi.DataType;

@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor
public class DispatcherVariableProvider implements VariableProvider {

    private final DownloadVariableService downloadVariableService;
    private final UploadVariableService uploadVariableService;

    @Override
    public DataSourcing getSourcing() {
        return DataSourcing.dispatcher;
    }

    @Override
    public List<AssetFile> prepareForDownloadingVariable(
            ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref, File taskDir, DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher,
            ProcessorTask task, MetadataParamsYaml.ProcessorState processorState,
            TaskParamsYaml.InputVariable variable) {

        try {

            if (variable.context==EnumsApi.VariableContext.array) {
                createDownloadTasksForArray(ref, variable.id, task.getTaskId(), taskDir,
                        dispatcher.dispatcherLookup, variable.getNullable());
            }
            else {
                DownloadVariableTask variableTask = new DownloadVariableTask(
                        ref, variable.id, variable.context, task.getTaskId(), taskDir,
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
            ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref,
            Long variableId, Long taskId, File taskDir, DispatcherLookupParamsYaml.DispatcherLookup dispatcherLookup,
            boolean nullable) throws IOException {
        DownloadVariableTask task = new DownloadVariableTask(
                ref, variableId, EnumsApi.VariableContext.local, taskId, taskDir, dispatcherLookup, nullable);
        downloadVariableService.add(task);

        AssetFile assetFile = AssetUtils.prepareFileForVariable(taskDir, variableId.toString(), null, DataType.variable);

        List<VariableArrayParamsYaml.Variable> variables = getVariablesForArray(assetFile);

        for (VariableArrayParamsYaml.Variable v : variables) {
            // element of array of variables can't be nullable
            DownloadVariableTask task1 = new DownloadVariableTask(
                    ref, v.id, v.dataType==DataType.variable ? EnumsApi.VariableContext.local : EnumsApi.VariableContext.global,
                    taskId, taskDir, dispatcherLookup, false);
            downloadVariableService.add(task1);
        }
    }

    private List<AssetFile> prepareArrayVariable(File taskDir, TaskParamsYaml.InputVariable variable) throws IOException {
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

    private List<VariableArrayParamsYaml.Variable> getVariablesForArray(AssetFile assetFile) throws IOException {
        List<VariableArrayParamsYaml.Variable> variables = new ArrayList<>();
        if (assetFile.isContent && !assetFile.isError) {
            String data = FileUtils.readFileToString(assetFile.file, StandardCharsets.UTF_8);
            VariableArrayParamsYaml vapy = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.to(data);
            variables.addAll(vapy.array);
        }
        return variables;
    }

    @Override
    @Nullable
    public FunctionApiData.SystemExecResult processOutputVariable(
            ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref, File taskDir, DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher,
            ProcessorTask task, MetadataParamsYaml.ProcessorState processorState,
            TaskParamsYaml.OutputVariable outputVariable, TaskParamsYaml.FunctionConfig functionConfig) {
        File outputVariableFile = new File(taskDir, ConstsApi.ARTIFACTS_DIR + File.separatorChar + outputVariable.id);
        if (outputVariableFile.exists()) {
            log.info("Register task for uploading result data to server, resultDataFile: {}", outputVariableFile.getPath());
            UploadVariableTask uploadVariableTask = new UploadVariableTask(task.taskId, outputVariableFile, outputVariable.id, ref, dispatcher.dispatcherLookup);
            uploadVariableService.add(uploadVariableTask);
            return null;
        }
        else if (Boolean.TRUE.equals(outputVariable.getNullable())) {
            log.info("Register an upload task for setting a variable #{} as null", outputVariable.id);
            UploadVariableTask uploadVariableTask = new UploadVariableTask(task.taskId, outputVariable.id, true, ref, dispatcher.dispatcherLookup);
            uploadVariableService.add(uploadVariableTask);
            return null;
        }
        else {
            String es = "Result data file doesn't exist, resultDataFile: " + outputVariableFile.getPath();
            log.error(es);
            return new FunctionApiData.SystemExecResult(functionConfig.code,false, -1, es);
        }
    }

    @Override
    public File getOutputVariableFromFile(
            ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref, File taskDir, DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher,
            ProcessorTask task, TaskParamsYaml.OutputVariable variable) {

        File resultDataFile = new File(taskDir, ConstsApi.ARTIFACTS_DIR + File.separatorChar + variable.id);
        return resultDataFile;
    }
}

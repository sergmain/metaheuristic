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

import ai.metaheuristic.ai.exceptions.VariableProviderException;
import ai.metaheuristic.ai.processor.ProcessorTaskService;
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import ai.metaheuristic.api.data.AssetFile;
import ai.metaheuristic.ai.utils.asset.AssetUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupExtendedParams;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorCoreTask;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.api.sourcing.DiskInfo;
import ai.metaheuristic.commons.yaml.env.EnvParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class DiskVariableProvider implements VariableProvider {

    private final ProcessorEnvironment processorEnvironment;
    private final ProcessorTaskService processorTaskService;

    @Nullable
    private static EnvParamsYaml.DiskStorage findDiskStorageByCode(List<EnvParamsYaml.DiskStorage> disk, String code) {
        for (EnvParamsYaml.DiskStorage diskStorage : disk) {
            if (Objects.equals(diskStorage.code, code)) {
                return diskStorage;
            }
        }
        return null;
    }

    @Override
    public EnumsApi.DataSourcing getSourcing() {
        return EnumsApi.DataSourcing.disk;
    }

    @Override
    public List<AssetFile> prepareForDownloadingVariable(
            ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Path taskDir, DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher,
            ProcessorCoreTask task, MetadataParamsYaml.ProcessorSession processorState,
            TaskParamsYaml.InputVariable variable) {

        if (variable.sourcing!= EnumsApi.DataSourcing.disk) {
            throw new VariableProviderException("#015.018 Wrong type of sourcing of data storage" + variable.sourcing);
        }
        if (variable.disk==null) {
            throw new VariableProviderException("#015.019 variable.sourcing==DataSourcing.disk but variable.disk is null");
        }
        DiskInfo diskInfo = variable.disk;

        EnvParamsYaml env = processorEnvironment.envParams.getEnvParamsYaml();
        EnvParamsYaml.DiskStorage diskStorage = findDiskStorageByCode(env.disk, diskInfo.code);
        if (diskStorage==null) {
            throw new VariableProviderException("#015.020 The disk storage wasn't found for code: " + diskInfo.code);
        }
        Path path = Path.of(diskStorage.path);
        if (Files.notExists(path)) {
            throw new VariableProviderException("#015.024 The path of disk storage doesn't exist: " + path.toAbsolutePath());
        }

        AssetFile assetFile;
        if (diskInfo.mask!=null && diskInfo.mask.equals("*")) {
            assetFile = new AssetFile();
            // TODO 2019.05.08 is this correct to declare file with '*' ?
            assetFile.setFile( path.resolve( "*") );
            assetFile.setProvided(true);
            assetFile.setContent(true);
            assetFile.setError(false);
            assetFile.setExist(true);
            assetFile.setFileLength(0);
        }
        else {
            if (diskInfo.mask!=null) {
                assetFile = AssetUtils.prepareAssetFile(path, null, diskInfo.mask);
            }
            else if (diskInfo.path!=null) {
                assetFile = AssetUtils.prepareAssetFile(path, null, diskInfo.path);
            }
            else {
                throw new IllegalStateException("diskInfo.mask and diskInfo.path are both blank");
            }
        }

        return Collections.singletonList(assetFile);
    }

    @Override
    public FunctionApiData.SystemExecResult processOutputVariable(
            ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Path taskDir, DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher,
            ProcessorCoreTask task, MetadataParamsYaml.ProcessorSession processorState,
            TaskParamsYaml.OutputVariable outputVariable, TaskParamsYaml.FunctionConfig functionConfig
    ) {
        Path outputVariableFile = taskDir.resolve(ConstsApi.ARTIFACTS_DIR).resolve(outputVariable.id.toString());
        if (Files.exists(outputVariableFile)) {
            log.info("The result variable #{} was already written to file {}, no need to upload to dispatcher", outputVariable.id, outputVariableFile.toAbsolutePath());
            processorTaskService.setVariableUploadedAndCompleted(core, task.taskId, outputVariable.id);
        }
        else if (Boolean.TRUE.equals(outputVariable.getNullable())) {
            log.info("The result variable #{} is nullable, no need to upload to dispatcher", outputVariable.id);
            processorTaskService.setVariableUploadedAndCompleted(core, task.taskId, outputVariable.id);
            return null;
        }
        else {
            String es = "#015.030 Result data file wasn't found, resultDataFile: " + outputVariableFile.toAbsolutePath();
            log.error(es);
            return new FunctionApiData.SystemExecResult(functionConfig.code, false, -1, es);
        }
        return null;
    }

    @Override
    public Path getOutputVariableFromFile(
            ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Path taskDir, DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher,
            ProcessorCoreTask task, TaskParamsYaml.OutputVariable variable) {

        EnvParamsYaml env = processorEnvironment.envParams.getEnvParamsYaml();
        if (variable.disk==null) {
            throw new VariableProviderException("#015.036 The disk storage wasn't defined in variable: " + variable);
        }
        EnvParamsYaml.DiskStorage diskStorage = findDiskStorageByCode(env.disk, variable.disk.code);
        if (diskStorage==null) {
            throw new VariableProviderException("#015.037 The disk storage wasn't found for code: " + variable.disk.code);
        }
        Path path = Path.of(diskStorage.path);
        if (Files.notExists(path)) {
            throw new VariableProviderException("#015.042 The path of disk storage doesn't exist: " + path.toAbsolutePath());
        }

        return path.resolve(Long.toString(variable.id));
    }

}

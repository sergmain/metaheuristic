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

import ai.metaheuristic.ai.exceptions.VariableProviderException;
import ai.metaheuristic.ai.processor.DispatcherLookupExtendedService;
import ai.metaheuristic.ai.processor.ProcessorTaskService;
import ai.metaheuristic.ai.processor.env.EnvService;
import ai.metaheuristic.ai.utils.asset.AssetFile;
import ai.metaheuristic.ai.utils.asset.AssetUtils;
import ai.metaheuristic.ai.yaml.env.DiskStorage;
import ai.metaheuristic.ai.yaml.env.EnvYaml;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorTask;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.sourcing.DiskInfo;
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
public class DiskVariableProvider implements VariableProvider {

    private final EnvService envService;
    private final ProcessorTaskService processorTaskService;

    public DiskVariableProvider(EnvService envService, ProcessorTaskService processorTaskService) {
        this.envService = envService;
        this.processorTaskService = processorTaskService;
    }

    @Override
    public EnumsApi.DataSourcing getSourcing() {
        return EnumsApi.DataSourcing.disk;
    }

    @Override
    public List<AssetFile> prepareForDownloadingVariable(
            File taskDir, DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher,
            ProcessorTask task, Metadata.DispatcherInfo dispatcherCode,
            TaskParamsYaml.InputVariable variable) {

        if (variable.sourcing!= EnumsApi.DataSourcing.disk) {
            throw new VariableProviderException("#015.018 Wrong type of sourcing of data storage" + variable.sourcing);
        }
        if (variable.disk==null) {
            throw new VariableProviderException("#015.019 variable.sourcing==DataSourcing.disk but variable.disk is null");
        }
        DiskInfo diskInfo = variable.disk;

        EnvYaml env = envService.getEnvYaml();
        DiskStorage diskStorage = env.findDiskStorageByCode(diskInfo.code);
        if (diskStorage==null) {
            throw new VariableProviderException("#015.020 The disk storage wasn't found for code: " + diskInfo.code);
        }
        File path = new File(diskStorage.path);
        if (!path.exists()) {
            throw new VariableProviderException("#015.024 The path of disk storage doesn't exist: " + path.getAbsolutePath());
        }

        AssetFile assetFile;
        if (diskInfo.mask!=null && diskInfo.mask.equals("*")) {
            assetFile = new AssetFile();
            // TODO 2019.05.08 is this correct to declare file with '*' ?
            assetFile.setFile( new File(path, "*") );
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
            File taskDir, DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher,
            ProcessorTask task, Metadata.DispatcherInfo dispatcherCode,
            Long outputVariableId, TaskParamsYaml.FunctionConfig functionConfig
    ) {
        File outputVariableFile = new File(taskDir, ConstsApi.ARTIFACTS_DIR + File.separatorChar + outputVariableId);
        if (outputVariableFile.exists()) {
            log.info("The result data was already written to file {}, no need to upload to dispatcher", outputVariableFile.getPath());
            processorTaskService.setVariableUploadedAndCompleted(dispatcher.dispatcherLookup.url, task.taskId, outputVariableId);
        } else {
            String es = "#015.030 Result data file wasn't found, resultDataFile: " + outputVariableFile.getPath();
            log.error(es);
            return new FunctionApiData.SystemExecResult(functionConfig.code, false, -1, es);
        }
        return null;
    }

    @Override
    public File getOutputVariableFromFile(
            File taskDir, DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher,
            ProcessorTask task, TaskParamsYaml.OutputVariable variable) {

        EnvYaml env = envService.getEnvYaml();
        if (variable.disk==null) {
            throw new VariableProviderException("#015.036 The disk storage wasn't defined in variable: " + variable);
        }
        DiskStorage diskStorage = env.findDiskStorageByCode(variable.disk.code);
        if (diskStorage==null) {
            throw new VariableProviderException("#015.037 The disk storage wasn't found for code: " + variable.disk.code);
        }
        File path = new File(diskStorage.path);
        if (!path.exists()) {
            throw new VariableProviderException("#015.042 The path of disk storage doesn't exist: " + path.getAbsolutePath());
        }

        return new File(path, Long.toString(variable.id));
    }

}

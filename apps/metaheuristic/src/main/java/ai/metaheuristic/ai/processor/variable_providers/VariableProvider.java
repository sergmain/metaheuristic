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

import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.api.data.AssetFile;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupExtendedParams;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorCoreTask;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.springframework.lang.Nullable;

import java.nio.file.Path;
import java.util.List;

public interface VariableProvider {
    List<AssetFile> prepareForDownloadingVariable(
            ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Path taskDir, DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher,
            ProcessorCoreTask task, MetadataParamsYaml.ProcessorSession processorState,
            TaskParamsYaml.InputVariable variable);

    @Nullable
    FunctionApiData.SystemExecResult processOutputVariable(
            ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Path taskDir, DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher,
            ProcessorCoreTask task, MetadataParamsYaml.ProcessorSession processorState,
            TaskParamsYaml.OutputVariable outputVariable,
            TaskParamsYaml.FunctionConfig functionConfig
    );

    Path getOutputVariableFromFile(
            ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Path taskDir,
            DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher,
            ProcessorCoreTask task, TaskParamsYaml.OutputVariable variable);

    EnumsApi.DataSourcing getSourcing();
}

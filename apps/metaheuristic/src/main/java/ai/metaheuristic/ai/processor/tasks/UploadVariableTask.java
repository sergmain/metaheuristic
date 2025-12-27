/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.processor.tasks;

import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

@Data
@EqualsAndHashCode(of={"core", "taskId", "variableId"}, callSuper = false)
public class UploadVariableTask extends ProcessorRestTask {
    public Long taskId;
    @Nullable
    public Path file = null;
    public Long variableId;
    public boolean nullified = false;
    public final ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core;
    public final DispatcherLookupParamsYaml.DispatcherLookup dispatcher;

    public UploadVariableTask(Long taskId, @Nullable Path file, Long variableId, ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, DispatcherLookupParamsYaml.DispatcherLookup dispatcher) {
        this.taskId = taskId;
        this.file = file;
        this.variableId = variableId;
        this.core = core;
        this.dispatcher = dispatcher;
    }

    public UploadVariableTask(Long taskId, Long variableId, boolean nullified, ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, DispatcherLookupParamsYaml.DispatcherLookup dispatcher) {
        this.taskId = taskId;
        this.variableId = variableId;
        this.nullified = nullified;
        this.core = core;
        this.dispatcher = dispatcher;
    }

    public ProcessorAndCoreData.DispatcherUrl getDispatcherUrl() {
        return core.dispatcherUrl;
    }
}

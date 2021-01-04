/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.lang.Nullable;

import java.io.File;

@Data
@EqualsAndHashCode(of={"taskId","variableId"}, callSuper = false)
public class UploadVariableTask extends ProcessorRestTask {
    public long taskId;
    @Nullable
    public File file = null;
    public Long variableId;
    public boolean nullified = false;
    public final String processorId;
    public final DispatcherLookupParamsYaml.DispatcherLookup dispatcher;
    public final String processorCode;

    public UploadVariableTask(long taskId, @Nullable File file, Long variableId, String processorId, DispatcherLookupParamsYaml.DispatcherLookup dispatcher, String processorCode) {
        this.taskId = taskId;
        this.file = file;
        this.variableId = variableId;
        this.processorId = processorId;
        this.dispatcher = dispatcher;
        this.processorCode = processorCode;
    }

    public UploadVariableTask(long taskId, Long variableId, boolean nullified, String processorId, DispatcherLookupParamsYaml.DispatcherLookup dispatcher, String processorCode) {
        this.taskId = taskId;
        this.variableId = variableId;
        this.nullified = nullified;
        this.processorId = processorId;
        this.dispatcher = dispatcher;
        this.processorCode = processorCode;
    }

    public ProcessorAndCoreData.DispatcherUrl getDispatcherUrl() {
        ProcessorAndCoreData.DispatcherUrl dispatcherUrl = new ProcessorAndCoreData.DispatcherUrl(dispatcher.url);
        return dispatcherUrl;
    }
}

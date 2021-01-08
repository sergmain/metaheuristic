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
package ai.metaheuristic.ai.processor.tasks;

import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.File;

@Data
@EqualsAndHashCode(of = {"ref", "variableId", "context"}, callSuper = false)
public class DownloadVariableTask extends ProcessorRestTask {
    public final String variableId;
    public final EnumsApi.VariableContext context;
    public final long taskId;
    public final File targetDir;
    public final boolean nullable;
    public final DispatcherLookupParamsYaml.DispatcherLookup dispatcher;
    public final ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref;

    public DownloadVariableTask(
            ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref,
            Long variableId, EnumsApi.VariableContext context, long taskId, File targetDir,
            DispatcherLookupParamsYaml.DispatcherLookup dispatcher, boolean nullable) {
        this(ref, variableId.toString(), context, taskId, targetDir, dispatcher, nullable);
    }

    public DownloadVariableTask(
            ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref,
            String variableId, EnumsApi.VariableContext context, long taskId, File targetDir,
            DispatcherLookupParamsYaml.DispatcherLookup dispatcher, boolean nullable) {
        this.ref = ref;
        this.variableId = variableId;
        this.context = context;
        this.taskId = taskId;
        this.targetDir = targetDir;
        this.dispatcher = dispatcher;
        this.nullable = nullable;
    }

    @Override
    public String toString() {
        return "DownloadVariableTask{" +
                "variableId='" + variableId + '\'' +
                "context='" + context + '\'' +
                ", targetDir=" + targetDir.getPath() +
                '}';
    }

    public ProcessorAndCoreData.DispatcherUrl getDispatcherUrl() {
        ProcessorAndCoreData.DispatcherUrl dispatcherUrl = new ProcessorAndCoreData.DispatcherUrl(dispatcher.url);
        return dispatcherUrl;
    }
}

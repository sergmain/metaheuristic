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

import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupConfig;
import ai.metaheuristic.api.EnumsApi;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.File;

@Data
@EqualsAndHashCode(of = {"variableId", "context"}, callSuper = false)
public class DownloadVariableTask extends ProcessorRestTask {
    public final String variableId;
    public final EnumsApi.VariableContext context;
    public final long taskId;
    public final File targetDir;
    public final Long chunkSize;
    public final boolean nullable;

    public DownloadVariableTask(
            Long variableId, EnumsApi.VariableContext context, long taskId, File targetDir, Long chunkSize,
                                DispatcherLookupConfig.DispatcherLookup dispatcher, String processorId, boolean nullable) {
        this(variableId.toString(), context, taskId, targetDir, chunkSize, dispatcher, processorId, nullable);
    }

    public DownloadVariableTask(
            String variableId, EnumsApi.VariableContext context, long taskId, File targetDir, Long chunkSize,
                                DispatcherLookupConfig.DispatcherLookup dispatcher, String processorId, boolean nullable) {
        this.variableId = variableId;
        this.context = context;
        this.taskId = taskId;
        this.targetDir = targetDir;
        this.chunkSize = chunkSize;
        this.dispatcher = dispatcher;
        this.processorId = processorId;
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
}

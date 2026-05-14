/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

/**
 * Per-task fetch unit for a sealed secret. Modeled on {@link DownloadVariableTask}.
 *
 * <p>One task per {@code (taskId, keyCode)} pair when the cache misses on
 * {@code (companyId, keyCode)}. The Processor enqueues the fetch and skips
 * the task launch cycle; the next launch attempt finds the populated cache
 * and proceeds.
 *
 * @author Sergio Lissner
 */
@Data
@EqualsAndHashCode(of = {"core", "companyId", "keyCode"}, callSuper = false)
public class DownloadSealedSecretTask extends ProcessorRestTask {
    public final ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core;
    public final DispatcherLookupParamsYaml.DispatcherLookup dispatcher;
    public final long taskId;
    public final long processorId;
    public final long companyId;
    public final String keyCode;

    public DownloadSealedSecretTask(
            ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core,
            DispatcherLookupParamsYaml.DispatcherLookup dispatcher,
            long taskId,
            long processorId,
            long companyId,
            String keyCode) {
        this.core = core;
        this.dispatcher = dispatcher;
        this.taskId = taskId;
        this.processorId = processorId;
        this.companyId = companyId;
        this.keyCode = keyCode;
    }

    @Override
    public String toString() {
        return "DownloadSealedSecretTask{taskId=" + taskId +
                ", companyId=" + companyId +
                ", keyCode='" + keyCode + '\'' + '}';
    }

    public ProcessorAndCoreData.DispatcherUrl getDispatcherUrl() {
        return new ProcessorAndCoreData.DispatcherUrl(dispatcher.url);
    }
}

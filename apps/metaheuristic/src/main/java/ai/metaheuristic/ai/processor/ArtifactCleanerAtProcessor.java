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
package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorTask;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor
public class ArtifactCleanerAtProcessor {

    private final ProcessorTaskService processorTaskService;
    private final CurrentExecState currentExecState;
    private final Globals globals;
    private final MetadataService metadataService;
    private final DispatcherLookupExtendedService dispatcherLookupExtendedService;

    public void fixedDelay() {
        for (ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref : metadataService.getAllEnabledRefs()) {

            File processorDir = new File(globals.processor.dir.dir, ref.processorCode);
            File processorTaskDir = new File(processorDir, Consts.TASK_DIR);

            if (!globals.processor.enabled || !currentExecState.isInited(ref.dispatcherUrl)) {
                // don't delete anything until the processor has received the list of actual ExecContexts
                continue;
            }

            MetadataParamsYaml.ProcessorState processorState = metadataService.processorStateByDispatcherUrl(ref);
            final File dispatcherDir = new File(processorTaskDir, processorState.dispatcherCode);
            if (!dispatcherDir.exists()) {
                dispatcherDir.mkdir();
            }

            List<ProcessorTask> all = processorTaskService.findAll(ref);
            for (ProcessorTask task : all) {
                if (currentExecState.isState(ref.dispatcherUrl, task.execContextId, EnumsApi.ExecContextState.DOESNT_EXIST)) {
                    log.warn("Delete obsolete task, id {}, url {}", task.getTaskId(), ref.dispatcherUrl.url);
                    processorTaskService.delete(ref, task.getTaskId());
                    continue;
                }
                if (task.clean && task.delivered && task.completed) {
                    log.info("Delete task with (task.clean && task.delivered && task.completed), id {}, url {}", task.getTaskId(), ref.dispatcherUrl.url);
                    processorTaskService.delete(ref, task.getTaskId());
                }
            }
        }
    }
}

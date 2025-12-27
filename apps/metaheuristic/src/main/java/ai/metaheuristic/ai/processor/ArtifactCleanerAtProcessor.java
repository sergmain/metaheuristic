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
package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorCoreTask;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ArtifactCleanerAtProcessor {

    private final ProcessorTaskService processorTaskService;
    private final CurrentExecState currentExecState;
    private final Globals globals;
    private final ProcessorEnvironment processorEnvironment;

    @SneakyThrows
    public void fixedDelay() {

        if (!globals.processor.enabled) {
            // don't delete anything until the processor has received the full list of actual ExecContexts
            return;
        }

        for (ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core : processorEnvironment.metadataParams.getAllEnabledRefsForCores()) {
            if (currentExecState.getCurrentInitState(core.dispatcherUrl) != Enums.ExecContextInitState.FULL) {
                // don't delete anything until the processor has received the full list of actual ExecContexts
                continue;
            }
            Path coreDir = globals.processorPath.resolve(core.coreCode);
            Path coreTaskDir = coreDir.resolve(Consts.TASK_DIR);

            MetadataParamsYaml.ProcessorSession processorState = processorEnvironment.metadataParams.processorStateByDispatcherUrl(core);
            final Path dispatcherDir = coreTaskDir.resolve(processorState.dispatcherCode);
            if (Files.notExists(dispatcherDir)) {
                Files.createDirectories(dispatcherDir);
            }
            List<ProcessorCoreTask> all = processorTaskService.findAllForCore(core);
            for (ProcessorCoreTask task : all) {
                if (currentExecState.isState(core.dispatcherUrl, task.execContextId, EnumsApi.ExecContextState.DOESNT_EXIST)) {
                    log.warn("Delete obsolete task, id {}, url {}", task.getTaskId(), core.dispatcherUrl.url);
                    for (Map.Entry<EnumsApi.ExecContextState, String> entry : currentExecState.getExecContextsNormalized(core.dispatcherUrl).entrySet()) {
                        log.warn("'   {}: {}", entry.getKey(), entry.getValue());
                    }
                    processorTaskService.delete(core, task.getTaskId());
                    continue;
                }
                if (task.clean && task.delivered && task.completed) {
                    log.info("Delete task with (task.clean && task.delivered && task.completed), id {}, url {}", task.getTaskId(), core.dispatcherUrl.url);
                    processorTaskService.delete(core, task.getTaskId());
                }
            }
        }
    }
}

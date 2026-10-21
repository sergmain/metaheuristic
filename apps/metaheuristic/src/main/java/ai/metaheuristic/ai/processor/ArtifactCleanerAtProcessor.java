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
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

        for (ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core : processorEnvironment.getProcessorEnv().metadataParams().getAllEnabledRefsForCores()) {
            if (currentExecState.getCurrentInitState(core.dispatcherUrl) != Enums.ExecContextInitState.FULL) {
                // don't delete anything until the processor has received the full list of actual ExecContexts
                continue;
            }
            Path coreDir = globals.processorPath.resolve(core.coreCode);
            Path coreTaskDir = coreDir.resolve(Consts.TASK_DIR);

            MetadataParamsYaml.ProcessorSession processorState = processorEnvironment.getProcessorEnv().metadataParams().processorStateByDispatcherUrl(core);
            final Path dispatcherDir = coreTaskDir.resolve(processorState.dispatcherCode);
            if (Files.notExists(dispatcherDir)) {
                Files.createDirectories(dispatcherDir);
            }
            // every Task with a dir, including the Tasks of FINISHED ExecContexts which weren't loaded at startup
            List<ProcessorTaskService.TaskShortInfo> all = processorTaskService.findAllTaskShortInfosForCore(core);
            // the loaded Tasks, key - taskId
            final Map<Long, ProcessorCoreTask> loaded = processorTaskService.findAllForCore(core).stream()
                    .collect(Collectors.toMap(t -> t.taskId, t -> t, (t1, t2) -> t1));
            for (ProcessorTaskService.TaskShortInfo info : all) {
                if (currentExecState.isState(core.dispatcherUrl, info.execContextId(), EnumsApi.ExecContextState.DOESNT_EXIST)) {
                    log.warn("Delete obsolete task, id {}, url {}", info.id(), core.dispatcherUrl.url);
                    for (Map.Entry<EnumsApi.ExecContextState, String> entry : currentExecState.getExecContextsNormalized(core.dispatcherUrl).entrySet()) {
                        log.warn("'   {}: {}", entry.getKey(), entry.getValue());
                    }
                    processorTaskService.delete(core, info.id());
                    continue;
                }
                // a not loaded Task - of a FINISHED ExecContext - has nothing more to clean than its whole dir, as above
                final ProcessorCoreTask task = loaded.get(info.id());
                if (task == null) {
                    continue;
                }
                final boolean done = task.delivered && task.completed;
                if (isTaskDirCleaningRequired(task.clean, task.cleaningPolicy, done)) {
                    log.info("Delete task with ((task.clean || cleaningPolicy==ALL) && task.delivered && task.completed), id {}, url {}", task.getTaskId(), core.dispatcherUrl.url);
                    processorTaskService.delete(core, task.getTaskId());
                    continue;
                }
                if (isAssetDirCleaningRequired(task.cleaningPolicy, done)) {
                    processorTaskService.deleteAssetDir(core, task.getTaskId());
                }
            }
        }
    }

    /**
     * The whole dir of a task is deleted either because SourceCode declared 'clean',
     * or because Function of that task declared cleaningPolicy==ALL - both mean the very same thing.
     * Static and free of any state on purpose - it's testable without a Spring context.
     */
    public static boolean isTaskDirCleaningRequired(boolean clean, EnumsApi.@Nullable CleaningPolicy cleaningPolicy, boolean done) {
        return done && (clean || cleaningPolicy==EnumsApi.CleaningPolicy.ALL);
    }

    /**
     * Only the 'asset' sub-dir of a task's dir is deleted, the rest of taskDir stays.
     * cleaningPolicy==ALL is intentionally not handled here, it's already covered by isTaskDirCleaningRequired().
     */
    public static boolean isAssetDirCleaningRequired(EnumsApi.@Nullable CleaningPolicy cleaningPolicy, boolean done) {
        return done && cleaningPolicy==EnumsApi.CleaningPolicy.ASSETS;
    }
}

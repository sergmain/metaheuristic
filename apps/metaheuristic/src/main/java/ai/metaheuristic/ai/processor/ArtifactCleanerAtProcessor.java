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
package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
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
        for (String dispatcherUrl : dispatcherLookupExtendedService.lookupExtendedMap.keySet()) {
            if (!globals.processorEnabled || !currentExecState.isInited(dispatcherUrl)) {
                // don't delete anything until the processor has received the list of actual ExecContexts
                continue;
            }

            Metadata.DispatcherInfo dispatcherCode = metadataService.dispatcherUrlAsCode(dispatcherUrl);
            final File dispatcherDir = new File(globals.processorTaskDir, dispatcherCode.code);
            if (!dispatcherDir.exists()) {
                dispatcherDir.mkdir();
            }

            List<ProcessorTask> all = processorTaskService.findAll(dispatcherUrl);
            for (ProcessorTask task : all) {
                if (currentExecState.isState(dispatcherUrl, task.execContextId, EnumsApi.ExecContextState.DOESNT_EXIST)) {
                    log.info("Delete obsolete task, id {}, url {}", task.getTaskId(), dispatcherUrl);
                    processorTaskService.delete(dispatcherUrl, task.getTaskId());
                    continue;
                }
                if (task.clean && task.delivered && task.completed) {
                    log.info("Delete task with (task.clean && task.delivered && task.completed), id {}, url {}", task.getTaskId(), dispatcherUrl);
                    processorTaskService.delete(dispatcherUrl, task.getTaskId());
                }
            }

/*
            synchronized (ProcessorSyncHolder.processorGlobalSync) {
                try {
                    final AtomicBoolean isEmpty = new AtomicBoolean(true);
                    Files.list(dispatcherDir.toPath()).forEach(s -> {
                        isEmpty.set(true);
                        try {
                            Files.list(s).forEach(t -> {
                                isEmpty.set(false);
                                try {
                                    File taskYaml = new File(t.toFile(), Consts.TASK_YAML);
                                    if (!taskYaml.exists()) {
                                        ProcessorTaskService.deleteDir(t.toFile(), "delete in ArtifactCleanerAtProcessor.fixedDelay()");
                                    }
                                } catch (IOException e) {
                                    log.error("#090.01 Error while deleting path {}, this isn't fatal error.", t);
                                }
                            });
                        } catch (AccessDeniedException e) {
                            // ok, may be later
                        } catch (IOException e) {
                            log.error("#090.07 Error while cleaning up broken tasks", e);
                        }
                    });
                } catch (IOException e) {
                    log.error("#090.07 Error while cleaning up broken tasks", e);
                }
            }
*/
        }
    }
}

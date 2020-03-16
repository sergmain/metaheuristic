/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.dispatcher.event;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionProcessor;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.dispatcher.task.TaskPersistencer;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 3/15/2020
 * Time: 10:58 PM
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("dispatcher")
public class TaskWithInternalContextEventService {

    private final TaskService taskService;
    private final TaskPersistencer taskPersistencer;
    private final TaskSyncService taskSyncService;
    private final InternalFunctionProcessor internalFunctionProcessor;
    private final ExecContextCache execContextCache;

    @Async
    @EventListener
    public void handleAsync(TaskWithInternalContextEvent event) {
        taskSyncService.getWithSync(event.taskId, (task) -> {
            if (task==null) {
                log.warn("step #1");
                return null;
            }
            task = taskPersistencer.toInProgressSimpleLambda(event.taskId, task);
            if (task==null) {
                log.warn("step #2");
                return null;
            }
            ExecContextImpl execContext = execContextCache.findById(task.execContextId);
            if (execContext==null) {
                taskPersistencer.finishTaskAsBrokenOrError(event.taskId, EnumsApi.TaskExecState.BROKEN);
                return null;
            }

            TaskParamsYaml taskParamsYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
            ExecContextParamsYaml execContextParamsYaml = execContext.getExecContextParamsYaml();
            ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(taskParamsYaml.task.processCode);
            if (p==null) {
                log.warn("#705.136 can't find process '"+taskParamsYaml.task.processCode+"' in execContext with Id #"+execContext.id);
                return null;
            }

//        String functionCode, Long sourceCodeId, Long execContextId,
//        String internalContextId, Map<String, List<String>> inputResourceIds) {
            internalFunctionProcessor.process(
                    taskParamsYaml.task.function.code, execContext.sourceCodeId, execContext.id, p.internalContextId, taskParamsYaml.task.inputs);

            return null;
        });

    }
}

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

package ai.metaheuristic.ai.dispatcher.exec_context_task_state;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.event.TransferStateFromTaskQueueToExecContextEvent;
import ai.metaheuristic.ai.dispatcher.event.UpdateTaskExecStatesInGraphEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskQueue;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 12/18/2020
 * Time: 6:37 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextTaskStateTopLevelService {

    private final ExecContextTaskStateService execContextTaskStateService;
    private final ExecContextTaskStateSyncService execContextTaskStateSyncService;
    private final ExecContextSyncService execContextSyncService;
    private final TaskRepository taskRepository;
    private final TaskSyncService taskSyncService;
    private final ExecContextCache execContextCache;

    @Async
    @EventListener
    public void updateTaskExecStatesInGraph(UpdateTaskExecStatesInGraphEvent event) {
        try {
            log.debug("call ExecContextTaskStateTopLevelService.updateTaskExecStatesInGraph({}, {})", event.execContextId, event.taskId);
            TaskImpl task = taskRepository.findById(event.taskId).orElse(null);
            if (task==null) {
                return;
            }
            TaskParamsYaml taskParams = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
            if (!event.execContextId.equals(task.execContextId)) {
                log.error("#417.020 (!execContextId.equals(task.execContextId))");
            }
            ExecContextImpl ec = execContextCache.findById(task.execContextId);
            if (ec==null) {
                return;
            }
/*
            final ExecContextImpl execContext;
            if (ec.execContextGraphId==null || ec.execContextTaskStateId==null) {
                execContext = execContextService.checkReferences(task.execContextId);
            }
            else {
                execContext = ec;
            }
            if (execContext==null) {
                return;
            }
*/
            execContextTaskStateSyncService.getWithSyncNullable(ec.execContextTaskStateId,
                    () -> taskSyncService.getWithSyncNullable(event.taskId,
                            () -> updateTaskExecStatesInGraph(ec.execContextGraphId, ec.execContextTaskStateId, event.taskId, EnumsApi.TaskExecState.from(task.execState), taskParams.task.taskContextId)));
        } catch (Throwable th) {
            log.error("#417.020 Error, need to investigate ", th);
        }
    }

    @Async
    @EventListener
    public void transferStateFromTaskQueueToExecContext(TransferStateFromTaskQueueToExecContextEvent event) {
        try {
            log.debug("call ExecContextTaskStateTopLevelService.transferStateFromTaskQueueToExecContext({})", event.execContextId);
            TaskQueue.TaskGroup taskGroup;
            int i = 1;
            while ((taskGroup = execContextSyncService.getWithSync(event.execContextId,
                    () -> transferStateFromTaskQueueToExecContext(event.execContextId)))!=null) {
                taskGroup.reset();
                i++;
                if (i>10_000) {
                    log.error("#417.040 To many calls to transferStateFromTaskQueueToExecContext()");
                    break;
                }
            }
            log.info("#417.060 transferStateFromTaskQueueToExecContext() was completed in {} loops", i-1);
        } catch (Throwable th) {
            log.error("Error, need to investigate ", th);
        }
    }

    @Nullable
    public TaskQueue.TaskGroup transferStateFromTaskQueueToExecContext(Long execContextId) {
        return execContextTaskStateService.transferStateFromTaskQueueToExecContext(execContextId);
    }

    private OperationStatusRest updateTaskExecStatesInGraph(Long execContextGraphId, Long execContextTaskStateId, Long taskId, EnumsApi.TaskExecState state, @Nullable String taskContextId) {
        return execContextTaskStateService.updateTaskExecStatesInGraph(execContextGraphId, execContextTaskStateId, taskId, state, taskContextId);
    }
}

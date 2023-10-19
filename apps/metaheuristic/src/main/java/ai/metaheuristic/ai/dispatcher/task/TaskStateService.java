/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextGraph;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.events.ChangeTaskStateToInitForChildrenTasksTxEvent;
import ai.metaheuristic.ai.dispatcher.event.events.TaskFinishWithErrorEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphCache;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.exceptions.CommonRollbackException;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * @author Serge
 * Date: 12/18/2020
 * Time: 12:58 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class TaskStateService {

    private final TaskFinishingTxService taskFinishingTxService;
    private final ExecContextGraphService execContextGraphService;
    private final ExecContextGraphCache execContextGraphCache;
    private final VariableTxService variableTxService;
    private final ExecContextCache execContextCache;
    private final TaskTxService taskTxService;
    private final TaskExecStateService taskExecStateService;
    private final TaskRepository taskRepository;
    private final GlobalVariableRepository globalVariableRepository;
    private final EventPublisherService eventPublisherService;

    @Async
    @EventListener
    public void finishWithErrorWithTx(TaskFinishWithErrorEvent event) {
        try {
            TaskSyncService.getWithSyncVoid(event.taskId,
                    () -> taskFinishingTxService.finishWithErrorWithTx(event.taskId, event.error));
        } catch (Throwable th) {
            log.error("189.040 Error, need to investigate ", th);
        }
    }

    @Async
    @EventListener
    public void changeTaskStateToInitForChildrenTasksTxEvent(ChangeTaskStateToInitForChildrenTasksTxEvent event) {
        TaskImpl task = taskRepository.findById(event.taskId).orElse(null);
        if (task==null) {
            return;
        }
        if (!EnumsApi.TaskExecState.isFinishedState(task.execState)) {
            log.warn("189.080 task #{} has a not finished state: {}", event.taskId, task.execState);
            return;
        }

        ExecContextImpl ec = execContextCache.findById(task.execContextId, true);
        if (ec==null) {
            return;
        }

        ExecContextGraph ecg = execContextGraphCache.findById(ec.execContextGraphId);
        if (ecg==null) {
            log.error("189.120 can't find ExecContextGraph #" + ec.execContextGraphId);
            return;
        }

        Set<ExecContextData.TaskVertex> subTasks = ExecContextGraphService.findDirectDescendants(ecg, event.taskId);
        for (ExecContextData.TaskVertex subTask : subTasks) {
            Set<ExecContextData.TaskVertex> parents = ExecContextGraphService.findDirectAncestors(ecg, subTask);
            boolean nextState = true;
            for (ExecContextData.TaskVertex vertex : parents) {
                TaskImpl t = taskRepository.findById(vertex.taskId).orElse(null);
                if (t==null) {
                    log.error("189.160 task #{} wasn't found", vertex.taskId);
                    continue;
                }
                if (!EnumsApi.TaskExecState.isFinishedState(t.execState)) {
                    log.warn("189.200 task #{} has un-finished state", vertex.taskId);
                    nextState = false;
                    break;
                }
            }
            if (nextState) {
                taskExecStateService.updateTaskExecStates(subTask.taskId, EnumsApi.TaskExecState.INIT);
            }
        }
    }
}

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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextGraph;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.event.events.ChangeTaskStateToInitForChildrenTasksEvent;
import ai.metaheuristic.ai.dispatcher.event.events.TaskFinishWithErrorEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphCache;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
    private final ExecContextGraphCache execContextGraphCache;
    private final ExecContextCache execContextCache;
    private final TaskExecStateService taskExecStateService;
    private final TaskRepository taskRepository;

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
    public void changeTaskStateToInitForChildrenTasksTxEvent(ChangeTaskStateToInitForChildrenTasksEvent event) {
        try {
            changeTaskStateToInitForChildrenTasksTxEventInternal(event);
        } catch (Throwable th) {
            log.error("189.060 Error", th);
        }
    }

    private boolean allDirectParentsDead(ExecContextGraph ecg, ExecContextData.TaskVertex subTask) {
        Set<ExecContextData.TaskVertex> parents = ExecContextGraphService.findDirectAncestors(ecg, subTask);
        if (parents.isEmpty()) {
            return false;
        }
        for (ExecContextData.TaskVertex parent : parents) {
            TaskImpl p = taskRepository.findByIdReadOnly(parent.taskId);
            if (p == null) {
                return false;
            }
            EnumsApi.TaskExecState st = EnumsApi.TaskExecState.from(p.execState);
            if (st != EnumsApi.TaskExecState.ERROR && st != EnumsApi.TaskExecState.SKIPPED) {
                return false;
            }
        }
        return true;
    }

    private void changeTaskStateToInitForChildrenTasksTxEventInternal(ChangeTaskStateToInitForChildrenTasksEvent event) {
        TxUtils.checkTxNotExists();

        TaskImpl task = taskRepository.findByIdReadOnly(event.taskId);
        if (task==null) {
            return;
        }
        log.warn("999.040 changeTaskStateToInitForChildren: task #{}, taskState: {}, execContextId: {}", event.taskId, EnumsApi.TaskExecState.from(task.execState), task.execContextId);
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
            boolean anyParentError = false;
            for (ExecContextData.TaskVertex vertex : parents) {
                TaskImpl t = taskRepository.findByIdReadOnly(vertex.taskId);
                if (t==null) {
                    log.error("189.160 task #{} wasn't found", vertex.taskId);
                    continue;
                }
                EnumsApi.TaskExecState state = EnumsApi.TaskExecState.from(t.execState);
                if (!EnumsApi.TaskExecState.isFinishedState(state)) {
                    log.warn("189.200 parent task #{} of task #{} has an un-finished state: {}", vertex.taskId, subTask.taskId, state);
                    nextState = false;
                    break;
                }
/*
// This change, made by Claude Opus 4.6, was reverted because it has broken processing of ExecContext with case in Production, when an error is in subProcess.
                if (state == EnumsApi.TaskExecState.ERROR || state == EnumsApi.TaskExecState.SKIPPED) {
                    anyParentError = true;
                }
            }
            if (anyParentError) {
                // Don't set children to INIT if any parent finished with ERROR,
                // UNLESS this is a leaf/mh.finish task (no outgoing edges) which should still proceed.
                // Children of errored tasks will be marked as SKIPPED by the graph update path
                // (via UpdateTaskExecStatesInExecContextEvent -> setStateForAllChildrenTasksInternal).
                // Note: SKIPPED parents (e.g. condition-gated mh.nop) do NOT block siblings —
                // SKIPPED is normal flow control, not an error.
                Set<ExecContextData.TaskVertex> subTaskDescendants = ExecContextGraphService.findDirectDescendants(ecg, subTask.taskId);
                if (!subTaskDescendants.isEmpty()) {
                    log.info("189.220 Skipping INIT for task #{} because a parent task finished with ERROR", subTask.taskId);
                    continue;
                }
*/
            }
            // F2: a SKIPPED child (e.g. a place-now grafted line = a dormant, objection-reopenable line) is a
            // terminal flow-control decision for THIS run; a completing parent must NOT un-skip it back to INIT.
            TaskImpl subTaskEntity = taskRepository.findByIdReadOnly(subTask.taskId);
            if (subTaskEntity != null && EnumsApi.TaskExecState.from(subTaskEntity.execState) == EnumsApi.TaskExecState.SKIPPED) {
                continue;
            }
            // A child whose EVERY direct parent is dead (ERROR/SKIPPED) is on a dead branch and must NOT be
            // advanced to INIT — the graph SKIPPED-cascade owns it. Advancing it here pulls it out of PRE_INIT
            // into INIT->NONE, which strands the run: the child sits NONE-but-completed and its own downstream
            // never releases (production ExecContext #11, task #562). This is the ALL-parents test on purpose:
            // a child with even ONE live parent is a legitimate convergence point (e.g. a condition-gated mh.nop
            // sibling rejoining) and MUST still advance — that is exactly what the earlier reverted `anyParentError`
            // variant got wrong. Leaves (mh.finish) and `tag terminal` vertices are exempt: they must always run.
            if (allDirectParentsDead(ecg, subTask)
                    && !ExecContextGraphService.findDirectDescendants(ecg, subTask.taskId).isEmpty()
                    && !ai.metaheuristic.ai.Consts.TAG_TERMINAL.equals(subTask.tag)) {
                log.info("189.230 not advancing task #{} to INIT: all direct parents are ERROR/SKIPPED (dead branch)", subTask.taskId);
                continue;
            }
            if (nextState) {
                log.warn("999.050 setting child task #{} to INIT, parent task #{}", subTask.taskId, event.taskId);
                TaskSyncService.getWithSyncVoid(subTask.taskId,
                    ()-> taskExecStateService.updateTaskExecStates(subTask.taskId, EnumsApi.TaskExecState.INIT));
            }
        }
    }
}

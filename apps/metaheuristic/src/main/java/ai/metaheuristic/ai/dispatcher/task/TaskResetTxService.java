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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.events.FindUnassignedTasksAndRegisterInQueueTxEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTaskResettingService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;

import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionRegisterService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextTaskStateRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.dispatcher.InternalFunction;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Sergio Lissner
 * Date: 3/7/2026
 * Time: 2:14 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class TaskResetTxService {

    private final ExecContextCache execContextCache;
    private final ExecContextTaskResettingService execContextTaskResettingService;
    private final ExecContextGraphService execContextGraphService;
    private final ExecContextTaskStateRepository execContextTaskStateRepository;
    private final EventPublisherService eventPublisherService;
    private final TaskRepository taskRepository;
    private final InternalFunctionRegisterService internalFunctionRegisterService;
    private final TaskFinishingTxService taskFinishingTxService;

    @Transactional
    public void resetTaskAndExecContextTx(Long execContextId, Long taskId) {
        resetTaskAndExecContext(execContextId, taskId);
    }

    public void resetTaskAndExecContext(Long execContextId, Long taskId) {
        TxUtils.checkTxExists();

        ExecContextSyncService.checkWriteLockPresent(execContextId);
        ExecContextImpl ec = execContextCache.findById(execContextId);
        if (ec == null) {
            return;
        }
        ExecContextTaskStateSyncService.checkWriteLockPresent(ec.execContextGraphId);
        ExecContextTaskStateSyncService.checkWriteLockPresent(ec.execContextTaskStateId);

        // Set ExecContext to STARTED if it was FINISHED
        if (ec.state == EnumsApi.ExecContextState.FINISHED.code) {
            ec.setState(EnumsApi.ExecContextState.STARTED.code);
            ec.setCompletedOn(null);
            execContextCache.save(ec);
            log.info("801.200 ExecContext #{} state changed from FINISHED to STARTED", execContextId);
        }

        // Reset the specified task in DB
        String taskContextId = TaskSyncService.getWithSyncNullable(taskId, () ->
            execContextTaskResettingService.resetTask(ec, taskId, EnumsApi.TaskExecState.INIT));

        if (taskContextId==null) {
            log.warn("801.205 Task #{} wasn't found in execContext #{}", taskId, execContextId);
            return;
        }

        // Find all descendant tasks in the DAG (downstream tasks including mh.finish)
        Set<ExecContextData.TaskVertex> descendants =
            execContextGraphService.findDescendants(execContextId, ec.execContextGraphId, taskId);

        List<String > allTaskContextIds = descendants.stream().map(ExecContextData.TaskVertex::getTaskContextId).collect(Collectors.toList());

        log.info("801.210 Found {} descendant tasks to reset for task #{}", descendants.size(), taskId);

        Set<String> subProcessesCtxId = ContextUtils.filterTaskContexts(taskContextId, allTaskContextIds);

        Set<String> dynamicTaskContextIds = new LinkedHashSet<>();
        for (ExecContextData.TaskVertex descendant : descendants) {
            if (subProcessesCtxId.contains(descendant.taskContextId)) {
                continue;
            }
            TaskImpl task = taskRepository.findById(descendant.taskId).orElse(null);
            if (task == null) {
                continue;
            }
            TaskParamsYaml tpy = task.getTaskParamsYaml();
            if (tpy.task.context != EnumsApi.FunctionExecContext.internal) {
                continue;
            }
            InternalFunction internalFunction = internalFunctionRegisterService.get(tpy.task.function.code);
            if (internalFunction == null) {
                String es = S.f("801.210 Internal function #%s wasn't found in Task #%s, will be marked as ERROR", tpy.task.function.code, taskId);
                log.error(es);
                taskFinishingTxService.finishWithError(task.id, es, EnumsApi.TaskExecState.ERROR);
                return;
            }
            if (!internalFunction.isDynamicSubProcesses(ec.asSimple(), tpy, taskId)) {
                continue;
            }
            dynamicTaskContextIds.add(tpy.task.taskContextId);
        }

        // the last element is at top layer, so skip last
        List<String> sorted = ContextUtils.sortSetAsTaskContextId(dynamicTaskContextIds);
        Set<Long> deletedTaskIds = new LinkedHashSet<>();
        if (sorted.size()>1) {
            ExecContextData.GraphAndStates graphAndStates = execContextGraphService.prepareGraphAndStates(
                ec.execContextGraphId, ec.execContextTaskStateId);

            List<String> actualListCtxId = sorted.subList(0, sorted.size()-1);
            List<ExecContextData.TaskVertex> forDeletion = descendants.stream().filter(v->actualListCtxId.contains(v.taskContextId)).collect(Collectors.toList());
            execContextGraphService.removeVertices(graphAndStates.graph(), forDeletion);
            forDeletion.forEach(v->{
                deletedTaskIds.add(v.taskId);
                TaskProviderTopLevelService.deregisterTask(execContextId, v.taskId);
            });
        }

        // Reset remaining descendant tasks (those NOT deleted from graph)
        for (ExecContextData.TaskVertex descendant : descendants) {
            if (deletedTaskIds.contains(descendant.taskId)) {
                log.info("801.214 Skipping reset of dynamically-deleted task #{}", descendant.taskId);
                continue;
            }
            TaskSyncService.getWithSyncVoid(descendant.taskId, () ->
                execContextTaskResettingService.resetTask(ec, descendant.taskId, EnumsApi.TaskExecState.PRE_INIT));
            log.info("801.215 Reset descendant task #{}", descendant.taskId);
        }

        int _=0;

        // Update graph state (ExecContextTaskState)
        ExecContextTaskState execContextTaskState = execContextTaskStateRepository.findById(ec.execContextTaskStateId).orElse(null);
        if (execContextTaskState == null) {
            log.error("801.230 ExecContextTaskState #{} not found", ec.execContextTaskStateId);
            return;
        }
        ExecContextTaskStateParamsYaml stateParams = execContextTaskState.getExecContextTaskStateParamsYaml();
        stateParams.states.put(taskId, EnumsApi.TaskExecState.INIT);
        for (ExecContextData.TaskVertex descendant : descendants) {
            if (deletedTaskIds.contains(descendant.taskId)) {
                // Remove state entry for deleted tasks
                stateParams.states.remove(descendant.taskId);
            }
            else {
                stateParams.states.put(descendant.taskId, EnumsApi.TaskExecState.NONE);
            }
        }
        execContextTaskState.updateParams(stateParams);
        execContextTaskStateRepository.save(execContextTaskState);

        // Trigger task assignment so the scheduler picks up the reset tasks
        eventPublisherService.handleFindUnassignedTasksAndRegisterInQueueEvent(new FindUnassignedTasksAndRegisterInQueueTxEvent());
    }

}

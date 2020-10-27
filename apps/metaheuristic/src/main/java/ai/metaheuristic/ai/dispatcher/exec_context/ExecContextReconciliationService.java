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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author Serge
 * Date: 10/12/2020
 * Time: 1:38 AM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextReconciliationService {

    private final ExecContextService execContextService;
    private final ExecContextGraphService execContextGraphService;
    private final TaskRepository taskRepository;
    private final ExecContextTaskStateService execContextTaskStateService;
    private final ExecContextSyncService execContextSyncService;
    private final ExecContextTaskResettingService execContextTaskResettingService;

    public ExecContextData.ReconciliationStatus reconcileStates(ExecContextImpl execContext) {
        TxUtils.checkTxNotExists();

        ExecContextData.ReconciliationStatus status = new ExecContextData.ReconciliationStatus(execContext.id);

        // Reconcile states in db and in graph
        List<ExecContextData.TaskVertex> rootVertices = execContextGraphService.findAllRootVertices(execContext);
        if (rootVertices.size()>1) {
            log.error("#307.020 Too many root vertices, count: " + rootVertices.size());
        }

        if (rootVertices.isEmpty()) {
            return status;
        }
        Set<ExecContextData.TaskVertex> vertices = execContextGraphService.findDescendants(execContext, rootVertices.get(0).taskId);

        final Map<Long, TaskData.TaskState> states = getExecStateOfTasks(execContext.id);

        for (ExecContextData.TaskVertex tv : vertices) {

            TaskData.TaskState taskState = states.get(tv.taskId);
            if (taskState==null) {
                status.isNullState.set(true);
            }
            else if (System.currentTimeMillis()-taskState.updatedOn>5_000 && tv.execState.value!=taskState.execState) {
                log.warn("#307.040 Found different states for task #"+tv.taskId+", " +
                        "db: "+ EnumsApi.TaskExecState.from(taskState.execState)+", " +
                        "graph: "+tv.execState);

/*
                if (taskState.execState== EnumsApi.TaskExecState.ERROR.value) {
                    finishWithErrorWithTx(tv.taskId, null, null);
                }
                else {
                    updateTaskExecStates(execContext, tv.taskId, taskState.execState, null);
                }
                break;
*/
            }
        }

        if (status.isNullState.get()) {
            log.info("#307.060 Found non-created task, graph consistency is failed");
            return status;
        }

        final Map<Long, TaskData.TaskState> newStates = getExecStateOfTasks(execContext.id);

        // fix actual state of tasks (can be as a result of OptimisticLockingException)
        // fix IN_PROCESSING state
        // find and reset all hanging up tasks
        newStates.entrySet().stream()
                .filter(e-> EnumsApi.TaskExecState.IN_PROGRESS.value==e.getValue().execState)
                .forEach(e->{
                    Long taskId = e.getKey();
                    TaskImpl task = taskRepository.findById(taskId).orElse(null);
                    if (task != null) {
                        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);

                        // did this task hang up at processor?
                        if (task.assignedOn!=null && tpy.task.timeoutBeforeTerminate != null && tpy.task.timeoutBeforeTerminate!=0L) {
                            // +2 is for waiting network communications at the last moment. i.e. wait for 4 seconds more
                            final long multiplyBy2 = (tpy.task.timeoutBeforeTerminate + 2) * 2 * 1000;
                            final long oneHourToMills = TimeUnit.HOURS.toMillis(1);
                            long timeout = Math.min(multiplyBy2, oneHourToMills);
                            if ((System.currentTimeMillis() - task.assignedOn) > timeout) {
                                log.info("#307.080 Reset task #{}, multiplyBy2: {}, timeout: {}", task.id, multiplyBy2, timeout);
                                status.taskForResettingIds.add(task.id);
                            }
                        }
                        else if (task.resultReceived && task.isCompleted) {
                            status.taskIsOkIds.add(task.id);
                        }
                    }
                });
        return status;
    }

    @NonNull
    private Map<Long, TaskData.TaskState> getExecStateOfTasks(Long execContextId) {
        List<Object[]> list = taskRepository.findAllExecStateByExecContextId(execContextId);

        Map<Long, TaskData.TaskState> states = new HashMap<>(list.size()+1);
        for (Object[] o : list) {
            TaskData.TaskState taskState = new TaskData.TaskState(o);
            states.put(taskState.taskId, taskState);
        }
        return states;
    }

    @Transactional
    public Void finishReconciliation(ExecContextData.ReconciliationStatus status) {
        execContextSyncService.checkWriteLockPresent(status.execContextId);

        if (!status.isNullState.get() && status.taskIsOkIds.isEmpty() && status.taskForResettingIds.isEmpty()) {
            return null;
        }
        ExecContextImpl execContext = execContextService.findById(status.execContextId);
        if (execContext==null) {
            return null;
        }
        if (status.isNullState.get()) {
            log.info("#307.100 Found non-created task, graph consistency is failed");
            execContext.completedOn = System.currentTimeMillis();
            execContext.state = EnumsApi.ExecContextState.ERROR.code;
            return null;
        }

        for (Long taskForResettingId : status.taskForResettingIds) {
            execContextTaskResettingService.resetTask(execContext, taskForResettingId);
        }
        for (Long taskIsOkId : status.taskIsOkIds) {
            TaskImpl task = taskRepository.findById(taskIsOkId).orElse(null);
            if (task==null) {
                log.error("#307.120 task is null");
                return null;
            }
            TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
            execContextTaskStateService.updateTaskExecStates(execContext, task, EnumsApi.TaskExecState.OK, tpy.task.taskContextId);
        }
        return null;
    }

}



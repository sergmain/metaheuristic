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
import ai.metaheuristic.ai.dispatcher.experiment_result.ExperimentResultService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Serge
 * Date: 7/16/2019
 * Time: 12:23 AM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextSchedulerService {

    private final ExecContextService execContextService;
    private final ExecContextRepository execContextRepository;
    private final TaskRepository taskRepository;
    private final ExperimentResultService experimentResultService;
    private final ExecContextFSM execContextFSM;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;

    public void updateExecContextStatuses(boolean needReconciliation) {
        List<ExecContextImpl> execContexts = execContextRepository.findByState(EnumsApi.ExecContextState.STARTED.code);
        for (ExecContextImpl execContext : execContexts) {
            updateExecContextStatus(execContext.id, needReconciliation);
        }

/*
        List<Long> execContextIds = execContextRepository.findIdsByExecState(EnumsApi.ExecContextState.EXPORTING_TO_EXPERIMENT_RESULT.code);
        for (Long execContextId : execContextIds) {
            log.info("Start exporting execContext #{} to ExperimentResult", execContextId);
            OperationStatusRest status;
            try {
                status = experimentResultService.storeExperimentToExperimentResult(execContextId);
            } catch (Exception e) {
                execContextFSM.toError(execContextId);
                continue;
            }

            if (status.status==EnumsApi.OperationStatus.OK) {
                log.info("Exporting of execContext #{} was finished", execContextId);
            } else {
                execContextFSM.toError(execContextId);
                log.error("#751.020 Error exporting experiment to ExperimentResult, execContextId #{}\n{}", execContextId, status.getErrorMessagesAsStr());
            }
        }
*/
    }

    /**
     *
     * @param execContextId ExecContext Id
     * @param needReconciliation
     * @return ExecContextImpl updated execContext
     */
    public void updateExecContextStatus(Long execContextId, boolean needReconciliation) {

        long countUnfinishedTasks = execContextService.getCountUnfinishedTasks(execContextId);
        if (countUnfinishedTasks==0) {
            // workaround for situation when states in graph and db are different
            reconcileStates(execContextId);
            countUnfinishedTasks = execContextService.getCountUnfinishedTasks(execContextId);
            if (countUnfinishedTasks==0) {
                log.info("ExecContext #{} was finished", execContextId);
                execContextFSM.toFinished(execContextId);
            }
        }
        else {
            if (needReconciliation) {
                reconcileStates(execContextId);
            }
        }
    }

    public void reconcileStates(Long execContextId) {
        List<Object[]> list = taskRepository.findAllExecStateByExecContextId(execContextId);

        // Reconcile states in db and in graph
        Map<Long, TaskData.TaskState> states = new HashMap<>(list.size()+1);
        for (Object[] o : list) {
            TaskData.TaskState taskState = new TaskData.TaskState(o);
            states.put(taskState.taskId, taskState);
        }

        Map<Long, TaskData.TaskState> taskStates = new HashMap<>();
        AtomicBoolean isNullState = new AtomicBoolean(false);

        List<ExecContextData.TaskVertex> vertices = execContextService.findAllVertices(execContextId);
        vertices.stream().parallel().forEach(tv -> {
            TaskData.TaskState taskState = states.get(tv.taskId);
            if (taskState==null) {
                isNullState.set(true);
            }
            else if (System.currentTimeMillis()-taskState.updatedOn>5_000 && tv.execState.value!=taskState.execState) {
                log.info("#751.040 Found different states for task #"+tv.taskId+", " +
                        "db: "+ EnumsApi.TaskExecState.from(taskState.execState)+", " +
                        "graph: "+tv.execState);
                taskStates.put(tv.taskId, taskState);
            }
        });

        if (isNullState.get()) {
            log.info("#751.060 Found non-created task, graph consistency is failed");
            execContextFSM.toError(execContextId);
        }
        else {
            execContextGraphTopLevelService.updateTaskExecStates(execContextId, taskStates);
        }

        // fix actual state of tasks (can be as a result of OptimisticLockingException)
        // fix IN_PROCESSING state
        // find and reset all hanging up tasks
        states.entrySet().stream()
                .filter(e-> EnumsApi.TaskExecState.IN_PROGRESS.value==e.getValue().execState)
                .forEach(e->{
                    Long taskId = e.getKey();
                    TaskImpl task = taskRepository.findById(taskId).orElse(null);
                    if (task != null) {
                        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);

                        // did this task hang up at processor?
                        if (task.assignedOn!=null && tpy.task.timeoutBeforeTerminate != null && tpy.task.timeoutBeforeTerminate!=0L) {
                            final long multiplyBy2 = tpy.task.timeoutBeforeTerminate * 2 * 1000;
                            final long oneHourToMills = TimeUnit.HOURS.toMillis(1);
                            long timeout = Math.min(multiplyBy2, oneHourToMills);
                            if ((System.currentTimeMillis() - task.assignedOn) > timeout) {
                                log.info("#751.080 Reset task #{}, multiplyBy2: {}, timeout: {}", task.id, multiplyBy2, timeout);
                                execContextService.resetTask(task.id);
                            }
                        }
                        else if (task.resultReceived && task.isCompleted) {
                            execContextGraphTopLevelService.updateTaskExecStateByExecContextId(execContextId, task.id, EnumsApi.TaskExecState.OK.value, tpy.task.taskContextId);
                        }
                    }
                });
    }
}

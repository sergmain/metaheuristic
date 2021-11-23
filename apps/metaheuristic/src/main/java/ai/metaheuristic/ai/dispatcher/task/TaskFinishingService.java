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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.cache.CacheService;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.UpdateTaskExecStatesInGraphTxEvent;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * @author Serge
 * Date: 12/17/2020
 * Time: 7:56 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class TaskFinishingService {

    private final DispatcherEventService dispatcherEventService;
    private final TaskService taskService;
    private final CacheService cacheService;
    private final TaskRepository taskRepository;
    private final TaskProviderTopLevelService taskProviderTopLevelService;
    private final EventPublisherService eventPublisherService;
    private final TaskStateService taskStateService;

    @Transactional
    public Void finishAsOkAndStoreVariable(Long taskId, ExecContextParamsYaml ecpy) {
        return finish(taskId, ecpy, true);
    }

    @Transactional
    public Void finishAsOk(Long taskId) {
        return finish(taskId, null, false);
    }

    private Void finish(Long taskId, @Nullable ExecContextParamsYaml ecpy, boolean store) {
        if (store && ecpy==null) {
            throw new IllegalStateException("(store && ecpy==null)");
        }
        TaskSyncService.checkWriteLockPresent(taskId);

        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("#319.100 Reporting about non-existed task #{}", taskId);
            return null;
        }

        eventPublisherService.publishUpdateTaskExecStatesInGraphTxEvent(new UpdateTaskExecStatesInGraphTxEvent(task.execContextId, taskId));

        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());

        taskStateService.updateTaskExecStates(task, EnumsApi.TaskExecState.OK, true);

        if (store && tpy.task.cache!=null && tpy.task.cache.enabled) {
            ExecContextParamsYaml.Process p = ecpy.findProcess(tpy.task.processCode);
            if (p==null) {
                log.warn("#319.120 Process {} wasn't found", tpy.task.processCode);
                return null;
            }
            cacheService.storeVariables(tpy, p.function);
        }
        return null;
    }

    @Transactional
    public Void finishWithErrorWithTx(Long taskId, String console) {
        try {
            TaskImpl task = taskRepository.findById(taskId).orElse(null);
            if (task==null) {
                log.warn("#319.140 task #{} wasn't found", taskId);
                return null;
            }
            if (task.execState==EnumsApi.TaskExecState.ERROR.value) {
                log.warn("#319.145 task #{} was already finished", taskId);
                return null;
            }
            String taskContextId = null;
            try {
                final TaskParamsYaml taskParamYaml;
                taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
                taskContextId = taskParamYaml.task.taskContextId;
            } catch (YAMLException e) {
                String es = S.f("#319.160 Task #%s has broken params yaml, error: %s, params:\n%s", task.getId(), e.toString(), task.getParams());
                log.error(es, e.getMessage());
            }

            finishTaskAsError(task, -10001, console);

            dispatcherEventService.publishTaskEvent(EnumsApi.DispatcherEventType.TASK_ERROR, task.processorId, task.id, task.execContextId);
        } catch (Throwable th) {
            log.warn("#319.165 Error while processing the task #{} with internal function. Error: {}", taskId, th.getMessage());
            log.warn("#319.170 Error", th);
            ExceptionUtils.rethrow(th);
        }
        return null;
    }

    private void finishTaskAsError(TaskImpl task, int exitCode, String console) {
        if (task.execState==EnumsApi.TaskExecState.ERROR.value && task.isCompleted && task.resultReceived && !S.b(task.functionExecResults)) {
            log.info("#319.200 (task.execState==EnumsApi.TaskExecState.ERROR && task.isCompleted && task.resultReceived && !S.b(task.functionExecResults)), task: {}", task.id);
            return;
        }
        task.setExecState(EnumsApi.TaskExecState.ERROR.value);
        task.setCompleted(true);
        task.setCompletedOn(System.currentTimeMillis());

        if (S.b(task.functionExecResults)) {
            TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
            FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec();
            functionExec.exec = new FunctionApiData.SystemExecResult(tpy.task.function.code, false, exitCode, console);
            task.setFunctionExecResults(FunctionExecUtils.toString(functionExec));
        }
        task.setResultReceived(true);
        task = taskService.save(task);

        taskProviderTopLevelService.setTaskExecState(task.execContextId, task.id, EnumsApi.TaskExecState.ERROR);
    }


}

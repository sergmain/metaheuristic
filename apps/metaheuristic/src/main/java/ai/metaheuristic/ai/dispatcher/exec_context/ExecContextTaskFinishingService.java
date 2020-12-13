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
import ai.metaheuristic.ai.dispatcher.cache.CacheService;
import ai.metaheuristic.ai.dispatcher.commons.DataHolder;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskExecStateService;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * @author Serge
 * Date: 10/3/2020
 * Time: 10:17 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextTaskFinishingService {

    private final ExecContextCache execContextCache;
    private final ExecContextGraphService execContextGraphService;
    private final ExecContextSyncService execContextSyncService;
    private final TaskExecStateService taskExecStateService;
    private final DispatcherEventService dispatcherEventService;
    private final ExecContextTaskStateService execContextTaskStateService;
    private final TaskService taskService;
    private final CacheService cacheService;
    private final TaskRepository taskRepository;
    private final TaskSyncService taskSyncService;

    @Transactional
    public Void finishAndStoreVariable(Long taskId, boolean checkCaching, DataHolder holder) {
        taskSyncService.checkWriteLockPresent(taskId);

        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("#303.100 Reporting about non-existed task #{}", taskId);
            return null;
        }

        execContextSyncService.checkWriteLockPresent(task.execContextId);

        final ExecContextImpl execContext = execContextCache.findById(task.execContextId);
        if (execContext==null) {
            // this execContext was deleted
            return null;
        }

        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());

        execContextTaskStateService.updateTaskExecStates(
                execContext, task, EnumsApi.TaskExecState.OK, tpy.task.taskContextId, true);

        if (checkCaching && tpy.task.cache!=null && tpy.task.cache.enabled) {
            ExecContextParamsYaml ecpy = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(execContext.params);
            ExecContextParamsYaml.Process p = ecpy.findProcess(tpy.task.processCode);
            if (p==null) {
                log.warn("#318.093 Process {} wasn't found", tpy.task.processCode);
                return null;
            }
            cacheService.storeVariables(tpy, p.function, holder);
        }
        return null;
    }

    @Transactional
    public Void finishWithErrorWithTx(Long taskId, String console) {
        taskSyncService.checkWriteLockPresent(taskId);
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("#318.095 task #{} wasn't found", taskId);
            return null;
        }
        String taskContextId = null;
        try {
            final TaskParamsYaml taskParamYaml;
            taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
            taskContextId = taskParamYaml.task.taskContextId;
        } catch (YAMLException e) {
            String es = S.f("#318.097 Task #%s has broken params yaml, error: %s, params:\n%s", task.getId(), e.toString(), task.getParams());
            log.error(es, e.getMessage());
        }

        return finishWithError(task, console, taskContextId, -10001);
    }

    public void finishWithError(TaskImpl task, @Nullable String taskContextId) {
        finishWithError(task, "#318.100 Task was finished with an unknown error, can't process it", taskContextId);
    }

    public Void finishWithError(TaskImpl task, String console, @Nullable String taskContextId) {
        return finishWithError(task, console, taskContextId, -10001);
    }

    public Void finishWithError(TaskImpl task, String console, @Nullable String taskContextId, int exitCode) {
        TxUtils.checkTxExists();
        execContextSyncService.checkWriteLockPresent(task.execContextId);
        taskSyncService.checkWriteLockPresent(task.id);

        finishTaskAsError(task, exitCode, console);

        final ExecContextImpl execContext = execContextCache.findById(task.execContextId);
        if (execContext!=null) {
            final ExecContextOperationStatusWithTaskList status = execContextGraphService.updateTaskExecState(
                    execContext, task.id, EnumsApi.TaskExecState.ERROR, taskContextId);
            taskExecStateService.updateTasksStateInDb(status);
        }
        dispatcherEventService.publishTaskEvent(EnumsApi.DispatcherEventType.TASK_ERROR,null, task.id, task.execContextId);
        return null;
    }

    private void finishTaskAsError(TaskImpl task, int exitCode, String console) {
        if (task.execState==EnumsApi.TaskExecState.ERROR.value && task.isCompleted && task.resultReceived && !S.b(task.functionExecResults)) {
            log.info("#318.120 (task.execState==state.value && task.isCompleted && task.resultReceived && !S.b(task.functionExecResults)), task: {}", task.id);
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
    }


}

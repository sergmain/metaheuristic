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

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskExecStateService;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final TaskRepository taskRepository;
    private final DispatcherEventService dispatcherEventService;
    private final ExecContextTaskStateService execContextTaskStateService;
    private final TaskService taskService;

    @Transactional
    public Void checkTaskCanBeFinishedWithTx(Long taskId) {
        checkTaskCanBeFinished(taskId);
        return null;
    }

    public void checkTaskCanBeFinished(Long taskId) {
        checkTaskCanBeFinished(taskRepository.findById(taskId).orElse(null));
    }

    public void checkTaskCanBeFinished(@Nullable TaskImpl task) {
        if (task==null) {
            return;
        }
        TxUtils.checkTxExists();
        execContextSyncService.checkWriteLockPresent(task.execContextId);

        EnumsApi.TaskExecState state = EnumsApi.TaskExecState.from(task.execState);

        if (state!=EnumsApi.TaskExecState.IN_PROGRESS) {
            log.info("#303.175 Task {} already isn't in IN_PROGRESS state, actual: {}", task.id, state);
            return;
        }

        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());

        if (!S.b(task.functionExecResults)) {
            if (!task.resultReceived) {
                throw new IllegalStateException("(!task.resultReceived)");
            }
            FunctionApiData.FunctionExec functionExec = FunctionExecUtils.to(task.functionExecResults);
            if (functionExec == null) {
                String es = "#303.120 Task #" + task.id + " has empty execResult";
                log.info(es);
                functionExec = new FunctionApiData.FunctionExec();
            }
            FunctionApiData.SystemExecResult systemExecResult = functionExec.generalExec != null ? functionExec.generalExec : functionExec.exec;
            if (!systemExecResult.isOk) {
                log.warn("#303.140 Task #{} finished with error, functionCode: {}, console: {}",
                        task.id,
                        systemExecResult.functionCode,
                        StringUtils.isNotBlank(systemExecResult.console) ? systemExecResult.console : "<console output is empty>");
            }

            if (!functionExec.allFunctionsAreOk()) {
                log.info("#303.160 store result with the state ERROR");
                finishWithError(
                        task.id,
                        StringUtils.isNotBlank(systemExecResult.console) ? systemExecResult.console : "<console output is empty>",
                        task.execContextId, tpy.task.taskContextId);

                dispatcherEventService.publishTaskEvent(EnumsApi.DispatcherEventType.TASK_ERROR,null, task.id, task.execContextId);
                return;
            }
        }

        boolean allUploaded = tpy.task.outputs.isEmpty() || tpy.task.outputs.stream()
                .filter(o->o.sourcing==EnumsApi.DataSourcing.dispatcher)
                .allMatch(o -> o.uploaded);

        if (task.resultReceived && allUploaded) {
            execContextTaskStateService.updateTaskExecStates(
                    execContextCache.findById(task.execContextId), task.id,
                    EnumsApi.TaskExecState.OK.value, tpy.task.taskContextId, true);
        }
    }

    public void markAsFinishedWithError(Long taskId, Long sourceCodeId, Long execContextId, TaskParamsYaml taskParamsYaml, InternalFunctionData.InternalFunctionProcessingResult result) {
        TxUtils.checkTxExists();

        log.error("#303.220 error type: {}, message: {}\n\tsourceCodeId: {}, execContextId: {}", result.processing, result.error, sourceCodeId, execContextId);
        final String console = "#303.240 Task #" + taskId + " was finished with status '" + result.processing + "', text of error: " + result.error;

        final String taskContextId = taskParamsYaml.task.taskContextId;

        finishWithError(taskId, console, execContextId, taskContextId);
    }

    @Transactional
    public void finishWithErrorWithTx(Long taskId, Long execContextId, @Nullable String taskContextId, @Nullable String params) {
        finishWithError(taskId, "#303.260 Task was finished with an unknown error, can't process it", execContextId, taskContextId);
    }

    @Transactional
    public Void finishWithErrorWithTx(Long taskId, String console, Long execContextId, @Nullable String taskContextId) {
        return finishWithError(taskId, console, execContextId, taskContextId);
    }

    public Void finishWithError(Long taskId, String console, Long execContextId, @Nullable String taskContextId) {
        return finishWithError(taskId, console, execContextId, taskContextId, -10001);
    }

    public Void finishWithError(Long taskId, String console, Long execContextId, @Nullable String taskContextId, int exitCode) {
        execContextSyncService.checkWriteLockPresent(execContextId);
        finishTaskAsError(taskId, exitCode, console);

        final ExecContextOperationStatusWithTaskList status = execContextGraphService.updateTaskExecState(
                execContextCache.findById(execContextId), taskId, EnumsApi.TaskExecState.ERROR.value, taskContextId);
        taskExecStateService.updateTasksStateInDb(status);

        dispatcherEventService.publishTaskEvent(EnumsApi.DispatcherEventType.TASK_ERROR,null, taskId, execContextId);
        return null;
    }

    private void finishTaskAsError(Long taskId, int exitCode, String console) {
        TxUtils.checkTxExists();
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("#305.080 Can't find Task for Id: {}", taskId);
            return;
        }
        execContextSyncService.checkWriteLockPresent(task.execContextId);
        if (task.execState==EnumsApi.TaskExecState.ERROR.value && task.isCompleted && task.resultReceived && !S.b(task.functionExecResults)) {
            log.info("#305.085 (task.execState==state.value && task.isCompleted && task.resultReceived && !S.b(task.functionExecResults)), task: {}", taskId);
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

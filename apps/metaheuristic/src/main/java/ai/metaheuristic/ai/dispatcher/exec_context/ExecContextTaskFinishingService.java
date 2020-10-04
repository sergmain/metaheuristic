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
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskExecStateService;
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

    public void checkTaskCanBeFinished(Long taskId) {
        checkTaskCanBeFinished(taskRepository.findById(taskId).orElse(null));
    }

    public void checkTaskCanBeFinished(@Nullable TaskImpl task) {
        if (task==null) {
            return;
        }
        TxUtils.checkTxExists();

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
                dispatcherEventService.publishTaskEvent(EnumsApi.DispatcherEventType.TASK_ERROR,null, task.id, task.execContextId);

                log.info("#303.160 store result with the state ERROR");
                finishWithErrorInternal(
                        task.id,
                        StringUtils.isNotBlank(systemExecResult.console) ? systemExecResult.console : "<console output is empty>",
                        task.execContextId, tpy.task.taskContextId);
                return;
            }
        }

        boolean allUploaded = tpy.task.outputs.isEmpty() || tpy.task.outputs.stream()
                .filter(o->o.sourcing==EnumsApi.DataSourcing.dispatcher)
                .allMatch(o -> o.uploaded);

        if (task.resultReceived && allUploaded) {
            execContextTaskStateService.updateTaskExecStates(execContextCache.findById(task.execContextId), task.id, EnumsApi.TaskExecState.OK.value, tpy.task.taskContextId);
        }
    }

    @Transactional
    public void finishWithError(Long taskId, Long execContextId, @Nullable String taskContextId, @Nullable String params) {
        execContextSyncService.checkWriteLockPresent(execContextId);
        finishWithErrorInternal(taskId, "#303.260 Task was finished with an unknown error, can't process it", execContextId, taskContextId);
    }

    @Transactional
    public Void finishWithError(Long taskId, String console, Long execContextId, @Nullable String taskContextId) {
        return finishWithErrorInternal(taskId, console, execContextId, taskContextId);
    }

    public Void finishWithErrorInternal(Long taskId, String console, Long execContextId, @Nullable String taskContextId) {
        execContextSyncService.checkWriteLockPresent(execContextId);
        taskExecStateService.finishTaskAsError(taskId, EnumsApi.TaskExecState.ERROR, -10001, console);

        final ExecContextOperationStatusWithTaskList status = execContextGraphService.updateTaskExecState(
                execContextCache.findById(execContextId), taskId, EnumsApi.TaskExecState.ERROR.value, taskContextId);
        taskExecStateService.updateTasksStateInDb(status);
        return null;
    }


}

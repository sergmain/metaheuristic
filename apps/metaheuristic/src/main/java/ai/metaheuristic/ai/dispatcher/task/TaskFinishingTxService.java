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

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.cache.CacheTxService;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.events.ChangeTaskStateToInitForChildrenTasksTxEvent;
import ai.metaheuristic.ai.dispatcher.event.events.UpdateTaskExecStatesInExecContextTxEvent;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.List;

/**
 * @author Serge
 * Date: 12/17/2020
 * Time: 7:56 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class TaskFinishingTxService {

    private final DispatcherEventService dispatcherEventService;
    private final TaskTxService taskService;
    private final CacheTxService cacheService;
    private final TaskRepository taskRepository;
    private final TaskProviderTopLevelService taskProviderTopLevelService;
    private final EventPublisherService eventPublisherService;
    private final ApplicationEventPublisher eventPublisher;
    private final TaskExecStateService taskExecStateService;

    @Transactional
    public void finishAsOkAndStoreVariable(Long taskId, ExecContextParamsYaml ecpy) {
        finishAsOk(taskId, ecpy, true);
    }

    @Transactional
    public void finishAsOk(Long taskId) {
        finishAsOk(taskId, null, false);
    }

    private void finishAsOk(Long taskId, @Nullable ExecContextParamsYaml ecpy, boolean store) {
        if (store && ecpy==null) {
            throw new IllegalStateException("(store && ecpy==null)");
        }
        TaskSyncService.checkWriteLockPresent(taskId);

        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("319.100 Reporting about non-existed task #{}", taskId);
            return;
        }

        eventPublisherService.publishUpdateTaskExecStatesInGraphTxEvent(new UpdateTaskExecStatesInExecContextTxEvent(task.execContextId, List.of(taskId)));

        taskExecStateService.updateTaskExecStates(task, EnumsApi.TaskExecState.OK, true);

        if (store) {
            TaskParamsYaml tpy = task.getTaskParamsYaml();

            if (tpy.task.cache!=null && tpy.task.cache.enabled) {
                ExecContextParamsYaml.Process p = ecpy.findProcess(tpy.task.processCode);
                if (p==null) {
                    log.warn("319.120 Process {} wasn't found", tpy.task.processCode);
                    return;
                }
                cacheService.storeVariables(tpy, p.function);
            }
        }
    }

    @Transactional
    public void finishWithErrorWithTx(Long taskId, String console) {
        finishWithError(taskId, console, EnumsApi.TaskExecState.ERROR_WITH_RECOVERY);
    }

    public void finishWithError(Long taskId, @Nullable String console, EnumsApi.TaskExecState targetState) {
        TxUtils.checkTxExists();

        try {
            TaskImpl task = taskRepository.findById(taskId).orElse(null);
            if (task==null) {
                log.warn("319.140 task #{} wasn't found", taskId);
                return;
            }
            if (task.execState==targetState.value && (task.execState==EnumsApi.TaskExecState.ERROR_WITH_RECOVERY.value || task.execState==EnumsApi.TaskExecState.ERROR.value)) {
                log.warn("319.145 task #{} was already finished", taskId);
                return;
            }
            TaskParamsYaml taskParamYaml=null;
            try {
                //noinspection unused
                taskParamYaml = task.getTaskParamsYaml();
            } catch (YAMLException e) {
                String es = S.f("319.160 Task #%s has broken params yaml, error: %s, params:\n%s", task.getId(), e.toString(), task.getParams());
                log.error(es, e.toString());
            }

            finishTaskAsError(task, console, targetState);

            dispatcherEventService.publishTaskEvent(EnumsApi.DispatcherEventType.TASK_ERROR, task.coreId, task.id, task.execContextId,
                    taskParamYaml==null ? null : taskParamYaml.task.context, taskParamYaml==null ? null : taskParamYaml.task.function.code );
        } catch (Throwable th) {
            log.warn("319.165 Error while processing the task #{} with internal function. Error: {}", taskId, th.getMessage());
            log.warn("319.170 Error", th);
            ExceptionUtils.rethrow(th);
        }
    }

    private void finishTaskAsError(TaskImpl task, @Nullable String console, EnumsApi.TaskExecState targetState) {
        final boolean updatePossible = targetState.value == task.execState &&
                                       (task.execState == EnumsApi.TaskExecState.ERROR_WITH_RECOVERY.value || task.execState == EnumsApi.TaskExecState.ERROR.value) &&
                                       task.completed!=0 && task.resultReceived!=0 && !S.b(task.functionExecResults);
        if (updatePossible) {
            log.info("319.200 task: #{}, updatePossible: {}", task.id, updatePossible);
            return;
        }
        task.setExecState(targetState.value);
        task.setCompleted(1);
        task.setCompletedOn(System.currentTimeMillis());

        if (S.b(task.functionExecResults)) {
            TaskParamsYaml tpy = task.getTaskParamsYaml();
            FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec();
            if (targetState== EnumsApi.TaskExecState.ERROR) {
                if (functionExec.exec==null) {
                    if (console==null) {
                        log.error("319.240 (console==null)");
                    }
                    functionExec.exec = new FunctionApiData.SystemExecResult(
                            tpy.task.function.code, false, -10001, console==null ? "<no console output>" : console);
                }
            }
            else {
                functionExec.exec = new FunctionApiData.SystemExecResult(tpy.task.function.code, false, -10001, console);
            }
            task.setFunctionExecResults(FunctionExecUtils.toString(functionExec));
        }
        task.setResultReceived(1);
        task = taskService.save(task);

        eventPublisherService.publishUpdateTaskExecStatesInGraphTxEvent(new UpdateTaskExecStatesInExecContextTxEvent(task.execContextId, List.of(task.id)));
        eventPublisher.publishEvent(new ChangeTaskStateToInitForChildrenTasksTxEvent(task.id));

        taskProviderTopLevelService.setTaskExecStateInQueue(task.execContextId, task.id, targetState);
    }


}

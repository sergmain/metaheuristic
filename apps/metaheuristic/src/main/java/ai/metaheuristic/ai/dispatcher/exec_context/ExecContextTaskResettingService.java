/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.events.SetTaskExecStateInQueueTxEvent;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextTaskStateRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskFinishingTxService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.dispatcher.task.TaskTxService;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.exceptions.BreakFromLambdaException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author Serge
 * Date: 10/15/2020
 * Time: 9:02 AM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecContextTaskResettingService {

    private final ExecContextCache execContextCache;
    private final VariableTxService variableTxService;
    private final TaskRepository taskRepository;
    private final TaskTxService taskTxService;
    private final EventPublisherService eventPublisherService;
    private final ExecContextTaskStateRepository execContextTaskStateRepository;
    private final TaskFinishingTxService taskFinishingTxService;

    @Transactional
    public void resetTasksWithErrorForRecovery(Long execContextId, List<TaskData.TaskWithRecoveryStatus> statuses) {
        ExecContextSyncService.checkWriteLockPresent(execContextId);

        ExecContextImpl ec = execContextCache.findById(execContextId);
        if (ec==null) {
            return;
        }

        ExecContextTaskState execContextTaskState = execContextTaskStateRepository.findById(ec.execContextTaskStateId).orElse(null);
        if (execContextTaskState==null) {
            log.error("155.030 ExecContextTaskState wasn't found for execContext #{}", execContextId);
            return;
        }
        ExecContextTaskStateParamsYaml ectspy = execContextTaskState.getExecContextTaskStateParamsYaml();

        for (TaskData.TaskWithRecoveryStatus status : statuses) {
            if (status.targetState== EnumsApi.TaskExecState.ERROR) {
                // console is null because an actual text of error was specified with TaskExecState.ERROR_WITH_RECOVERY
                TaskSyncService.getWithSyncVoid(status.taskId,
                        ()-> taskFinishingTxService.finishWithError(status.taskId, null, EnumsApi.TaskExecState.ERROR));
            }
            else if (status.targetState==EnumsApi.TaskExecState.NONE) {
                TaskSyncService.getWithSyncVoid(status.taskId, ()->resetTask(ec, status.taskId, EnumsApi.TaskExecState.NONE));
                ectspy.triesWasMade.put(status.taskId, status.triesWasMade);
            }
            else {
                throw new IllegalStateException("status.targetState==");
            }
        }
        execContextTaskState.updateParams(ectspy);
        execContextTaskStateRepository.save(execContextTaskState);
    }

    @Transactional
    public void resetTaskWithTx(Long execContextId, Long taskId) {
        TaskSyncService.checkWriteLockNotPresent(taskId);

        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return;
        }
        TaskSyncService.getWithSyncVoid(taskId, ()->resetTask(execContext, taskId));
    }

    public void resetTask(ExecContextImpl execContext, Long taskId) {
        resetTask(execContext, taskId, null);
    }

    public void resetTask(ExecContextImpl execContext, Long taskId, @Nullable EnumsApi.TaskExecState targetExecState) {
        TxUtils.checkTxExists();
        ExecContextSyncService.checkWriteLockPresent(execContext.id);
        TaskSyncService.checkWriteLockPresent(taskId);

        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        log.info("155.080 Start re-setting task #{}", taskId);
        if (task == null) {
            String es = S.f("155.120 Found a non-existed task, graph consistency for execContextId #%s is failed",
                    execContext.id);
            log.error(es);
            execContext.completedOn = System.currentTimeMillis();
            execContext.state = EnumsApi.ExecContextState.ERROR.code;
            return;
        }

        TaskParamsYaml taskParams = task.getTaskParamsYaml();
        final ExecContextParamsYaml.Process process = execContext.getExecContextParamsYaml().findProcess(taskParams.task.processCode);
        if (process==null) {
            throw new BreakFromLambdaException("155.160 Process '" + taskParams.task.processCode + "' wasn't found");
        }

        task.setFunctionExecResults(null);
        task.setCoreId(null);
        task.setAssignedOn(null);
        task.setCompleted(0);
        task.setCompletedOn(null);
        if (targetExecState==null) {
            task.execState = process.cache != null && process.cache.enabled ? EnumsApi.TaskExecState.CHECK_CACHE.value : EnumsApi.TaskExecState.NONE.value;
        }
        else {
            task.execState = targetExecState.value;
        }
        task.setResultReceived(1);
        task.setResultResourceScheduledOn(0);
        taskTxService.save(task);
        for (TaskParamsYaml.OutputVariable output : taskParams.task.outputs) {
            if (output.context== EnumsApi.VariableContext.global) {
                throw new IllegalStateException("155.200 (output.context== EnumsApi.VariableContext.global)");
            }
            VariableSyncService.getWithSyncVoidForCreation(output.id, ()-> variableTxService.resetVariable(execContext.id, output.id));
        }

        eventPublisherService.publishSetTaskExecStateInQueueTxEvent(
                new SetTaskExecStateInQueueTxEvent(task.execContextId, task.id, EnumsApi.TaskExecState.from(task.execState), null, null, null));

        // we don't have to un-register task because it could un-register already de-registered task.
        // actual deregistering will be done via reconsiliationService
//        eventPublisherService.publishUnAssignTaskTxEventAfterCommit(new UnAssignTaskTxAfterCommitEvent(task.execContextId, task.id));

        log.info("155.240 task #{} and its output variables were re-setted to initial state", taskId);
    }

}

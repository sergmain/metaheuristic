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

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.event.DeregisterTasksByExecContextIdEvent;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.function.BiFunction;

/**
 * @author Serge
 * Date: 12/17/2020
 * Time: 8:00 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class TaskFinishingTopLevelService {

    private final DispatcherEventService dispatcherEventService;
    private final TaskRepository taskRepository;
    private final TaskFinishingService taskFinishingService;
    private final ExecContextCache execContextCache;
    private final ApplicationEventPublisher eventPublisher;

    public void checkTaskCanBeFinished(Long taskId) {
        checkTaskCanBeFinishedInternal(taskId, this::finishAndStoreVariableInternal );
    }

    private void checkTaskCanBeFinishedInternal(Long taskId, BiFunction<Long, ExecContextParamsYaml, Void> finishAndStoreVariableFunction) {
        TxUtils.checkTxNotExists();
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.warn("#318.010 Reporting about non-existed task #{}", taskId);
            return;
        }

        EnumsApi.TaskExecState state = EnumsApi.TaskExecState.from(task.execState);

        switch (state) {
            case ERROR:
            case OK:
            case SKIPPED:
                // ---> This is a normal operation. do nothing
                return;

            case NONE:
                log.info("#318.020 Task {} has a state as NONE, do nothing", task.id);
                // ---> This is a normal operation. do nothing
                return;

            case IN_PROGRESS:
            case CHECK_CACHE:
                break;

            case NOT_USED_ANYMORE:
                throw new IllegalStateException("Must not happened");
        }

        if (!S.b(task.functionExecResults)) {
            if (!task.resultReceived) {
                throw new IllegalStateException("(!task.resultReceived)");
            }
            FunctionApiData.FunctionExec functionExec = FunctionExecUtils.to(task.functionExecResults);
            if (functionExec == null) {
                String es = "#318.040 Task #" + task.id + " has empty execResult";
                log.info(es);
                functionExec = new FunctionApiData.FunctionExec();
            }
            FunctionApiData.SystemExecResult systemExecResult = functionExec.generalExec != null ? functionExec.generalExec : functionExec.exec;
            if (!systemExecResult.isOk) {
                log.warn("#318.060 Task #{} finished with error, functionCode: {}, console: {}",
                        task.id,
                        systemExecResult.functionCode,
                        StringUtils.isNotBlank(systemExecResult.console) ? systemExecResult.console : "<console output is empty>");
            }

            if (!functionExec.allFunctionsAreOk()) {
                log.info("#318.080 store result with the state ERROR");
                TaskSyncService.getWithSyncNullable(task.id,
                        () -> finishWithErrorWithInternal(
                                taskId, StringUtils.isNotBlank(systemExecResult.console) ? systemExecResult.console : "<console output is empty>"));

                dispatcherEventService.publishTaskEvent(EnumsApi.DispatcherEventType.TASK_ERROR, null, task.id, task.execContextId);
                return;
            }
        }

        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
        boolean allUploaded = tpy.task.outputs.isEmpty() || tpy.task.outputs.stream()
                .filter(o->o.sourcing==EnumsApi.DataSourcing.dispatcher)
                .allMatch(o -> o.uploaded);

        if (task.resultReceived && allUploaded) {

            ExecContextImpl execContext = execContextCache.findById(task.execContextId);
            if (execContext==null) {
                eventPublisher.publishEvent( new DeregisterTasksByExecContextIdEvent(task.execContextId) );
                return;
            }

            TaskSyncService.getWithSyncNullable(task.id,
                    () -> finishAndStoreVariableFunction.apply(taskId, execContext.getExecContextParamsYaml()));
        }
    }

    // this method is here because there was a problem with transactional method called from lambda
    private Void finishAndStoreVariableInternal(Long taskId, ExecContextParamsYaml ecpy) {
        return taskFinishingService.finishAsOkAndStoreVariable(taskId, ecpy);
    }

    private Void finishWithErrorWithInternal(Long taskId, String console) {
        return taskFinishingService.finishWithErrorWithTx(taskId, console);
    }



}

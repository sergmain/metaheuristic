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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.commons.DataHolder;
import ai.metaheuristic.ai.dispatcher.event.DeregisterTasksByExecContextIdEvent;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

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
    private final TaskStateService taskStateService;
    private final TaskSyncService taskSyncService;
    private final ExecContextCache execContextCache;
    private final ApplicationEventPublisher applicationEventPublisher;

    public void checkTaskCanBeFinished(Long taskId, boolean checkCaching) {
        TxUtils.checkTxNotExists();
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("#303.100 Reporting about non-existed task #{}", taskId);
            return;
        }

        EnumsApi.TaskExecState state = EnumsApi.TaskExecState.from(task.execState);

        if (state!=EnumsApi.TaskExecState.IN_PROGRESS && state!=EnumsApi.TaskExecState.CHECK_CACHE) {
            log.info("#318.020 Task {} already isn't in IN_PROGRESS or CHECK_CACHE state, actual: {}", task.id, state);
            return;
        }

        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());

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
                taskSyncService.getWithSyncNullable(task.id,
                        () -> taskStateService.finishWithErrorWithTx(
                                taskId, StringUtils.isNotBlank(systemExecResult.console) ? systemExecResult.console : "<console output is empty>"));

                dispatcherEventService.publishTaskEvent(EnumsApi.DispatcherEventType.TASK_ERROR,null, task.id, task.execContextId);
                return;
            }
        }

        boolean allUploaded = tpy.task.outputs.isEmpty() || tpy.task.outputs.stream()
                .filter(o->o.sourcing==EnumsApi.DataSourcing.dispatcher)
                .allMatch(o -> o.uploaded);

        if (task.resultReceived && allUploaded) {

            ExecContextImpl execContext = execContextCache.findById(task.execContextId);
            if (execContext==null) {
                applicationEventPublisher.publishEvent( new DeregisterTasksByExecContextIdEvent(task.execContextId) );
                return;
            }

            try (DataHolder holder = new DataHolder()) {
                taskSyncService.getWithSyncNullable(task.id,
                        () -> taskStateService.finishAndStoreVariable(
                                taskId, checkCaching, holder, ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(execContext.params)));
            }
        }
    }



}

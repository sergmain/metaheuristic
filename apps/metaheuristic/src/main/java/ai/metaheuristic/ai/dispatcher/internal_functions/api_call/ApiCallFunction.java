/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.internal_functions.api_call;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.event.FindUnassignedTasksAndRegisterInQueueTxEvent;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.SubProcessesTxService;
import ai.metaheuristic.ai.mhbp.provider.ProviderData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * @author Sergio Lissner
 * Date: 5/14/2023
 * Time: 2:17 AM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class ApiCallFunction implements InternalFunction {

    private final ApplicationEventPublisher eventPublisher;
    private final ApiCallService apiCallService;
    private final SubProcessesTxService subProcessesTxService;

    @Override
    public String getCode() {
        return Consts.MH_API_CALL_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_API_CALL_FUNCTION;
    }

    public boolean isCachable() {
        return true;
    }

    @SneakyThrows
    @Override
    public void process(
            ExecContextData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId,
            TaskParamsYaml taskParamsYaml) {

        ProviderData.QuestionAndAnswer answer = apiCallService.callApi(simpleExecContext, taskId, taskContextId, taskParamsYaml);

        ExecContextGraphSyncService.getWithSync(simpleExecContext.execContextGraphId, ()->
                ExecContextTaskStateSyncService.getWithSync(simpleExecContext.execContextTaskStateId, ()->
                        subProcessesTxService.processSubProcesses(simpleExecContext, taskId, taskParamsYaml)));

        eventPublisher.publishEvent(new FindUnassignedTasksAndRegisterInQueueTxEvent());

        //noinspection unused
        int i=0;
    }

}

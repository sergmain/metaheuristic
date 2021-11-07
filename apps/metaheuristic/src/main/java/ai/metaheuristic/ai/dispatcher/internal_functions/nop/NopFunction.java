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

package ai.metaheuristic.ai.dispatcher.internal_functions.nop;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 8/13/2020
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class NopFunction implements InternalFunction {

    private final NopService nopService;

    @Override
    public String getCode() {
        return Consts.MH_NOP_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_NOP_FUNCTION;
    }

    @Override
    public void process(
            ExecContextData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId,
            TaskParamsYaml taskParamsYaml) {

        ExecContextGraphSyncService.getWithSync(simpleExecContext.execContextGraphId, ()->
                ExecContextTaskStateSyncService.getWithSync(simpleExecContext.execContextTaskStateId, ()->
                    nopService.processSubProcesses(simpleExecContext, taskId, taskParamsYaml)));

        log.debug("#055.020 Nop function was invoked");
    }
}

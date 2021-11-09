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

package ai.metaheuristic.ai.dispatcher.internal_functions.finish;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.variable.VariableTopLevelService;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 3/13/2020
 * Time: 11:13 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class FinishFunction implements InternalFunction {

    private final ExecContextFSM execContextFSM;
    private final VariableTopLevelService variableTopLevelService;

    @Override
    public String getCode() {
        return Consts.MH_FINISH_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_FINISH_FUNCTION;
    }

    @Override
    public void process(
            ExecContextData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId,
            TaskParamsYaml taskParamsYaml) {
        TxUtils.checkTxNotExists();

        try {
            variableTopLevelService.checkFinalOutputVariables(taskParamsYaml, simpleExecContext.execContextId);

            log.info(S.f("#054.010 change state of task #%s with internal function %s to 'OK'", taskId, Consts.MH_FINISH_FUNCTION));
            ExecContextSyncService.getWithSync(simpleExecContext.execContextId,
                    () -> execContextFSM.toFinished(simpleExecContext.execContextId));
        } catch (Throwable e) {
            log.error("#054.040 error", e);
            ExecContextSyncService.getWithSync(simpleExecContext.execContextId,
                    () -> execContextFSM.changeExecContextStateWithTx(EnumsApi.ExecContextState.ERROR, simpleExecContext.execContextId, simpleExecContext.companyId));
        }
    }
}

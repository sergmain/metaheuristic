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
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import static ai.metaheuristic.ai.dispatcher.data.InternalFunctionData.InternalFunctionProcessingResult;

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

    @Override
    public String getCode() {
        return Consts.MH_FINISH_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_FINISH_FUNCTION;
    }

    @Override
    public InternalFunctionProcessingResult process(
            ExecContextImpl execContext, TaskImpl task, String taskContextId,
            ExecContextParamsYaml.VariableDeclaration variableDeclaration,
            TaskParamsYaml taskParamsYaml) {
        TxUtils.checkTxExists();

        log.info(S.f("#054.010 Mark task #%s with internal function %s as 'OK'", task, Consts.MH_FINISH_FUNCTION));
        execContextFSM.toFinished(execContext);
        return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.ok);
    }
}

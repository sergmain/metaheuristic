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

package ai.metaheuristic.ai.dispatcher.internal_functions.exec_source_code;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.*;

/**
 * @author Serge
 * Date: 6/10/2021
 * Time: 3:26 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class ExecSourceCodeFunction implements InternalFunction {

    private final SourceCodeRepository sourceCodeRepository;
    private final ExecContextTopLevelService execContextTopLevelService;
    private final ExecContextCreatorTopLevelService execContextCreatorTopLevelService;

    @Override
    public String getCode() {
        return Consts.MH_EXEC_SOURCE_CODE_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_EXEC_SOURCE_CODE_FUNCTION;
    }

    public boolean isLongRunning() {
        return true;
    }

    @Override
    public void process(ExecContextData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId, TaskParamsYaml taskParamsYaml) {
        TxUtils.checkTxNotExists();

        String scUid = MetaUtils.getValue(taskParamsYaml.task.metas, "source-code-uid");
        if (S.b(scUid)) {
            throw new InternalFunctionException(
                    new InternalFunctionData.InternalFunctionProcessingResult(
                            meta_not_found,"#508.020 meta 'source-code-uid' wasn't found"));
        }

        SourceCodeImpl sc = sourceCodeRepository.findByUid(scUid);
        if (sc==null) {
            throw new InternalFunctionException(
                    new InternalFunctionData.InternalFunctionProcessingResult(
                            source_code_not_found,"#508.040 sourceCode '"+scUid+"' wasn't found"));
        }

        ExecContextCreatorService.ExecContextCreationResult execContextResultRest =
                execContextCreatorTopLevelService.createExecContextAndStart(sc.id, simpleExecContext.companyId, false);

        if (execContextResultRest.isErrorMessages()) {
            throw new InternalFunctionException(
                    new InternalFunctionData.InternalFunctionProcessingResult(
                            exec_context_creation_error,
                            "#508.060 execContext for sourceCode '"+scUid+"' wasn't created, error: " + execContextResultRest.getErrorMessagesAsStr()));
        }

        /// copy variables
        // ...

        OperationStatusRest operationStatusRest = execContextTopLevelService.execContextTargetState(
                execContextResultRest.execContext.id, EnumsApi.ExecContextState.STARTED, simpleExecContext.companyId);

        if (operationStatusRest.isErrorMessages()) {

            throw new InternalFunctionException(
                    new InternalFunctionData.InternalFunctionProcessingResult(
                            exec_context_starting_error,
                            "#508.080 execContext #"+execContextResultRest.execContext.id+" for sourceCode '"+scUid+"' can't be started, error: " + operationStatusRest.getErrorMessagesAsStr()));
        }

    }
}

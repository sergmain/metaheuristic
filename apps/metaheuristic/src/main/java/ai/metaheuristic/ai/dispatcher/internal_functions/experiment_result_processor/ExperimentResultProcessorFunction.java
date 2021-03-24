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

package ai.metaheuristic.ai.dispatcher.internal_functions.experiment_result_processor;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.experiment_result.ExperimentResultService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 4/19/2020
 * Time: 8:05 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class ExperimentResultProcessorFunction implements InternalFunction {

    private final ExperimentResultService experimentResultService;
    private final ExecContextSyncService execContextSyncService;

    @Override
    public String getCode() {
        return Consts.MH_EXPERIMENT_RESULT_PROCESSOR;
    }

    @Override
    public String getName() {
        return Consts.MH_EXPERIMENT_RESULT_PROCESSOR;
    }

    @Override
    public void process(
            ExecContextImpl execContext, TaskImpl task, String taskContextId,
            ExecContextParamsYaml.VariableDeclaration variableDeclaration,
            TaskParamsYaml taskParamsYaml) {
        TxUtils.checkTxExists();
        execContextSyncService.checkWriteLockPresent(execContext.id);

        try {
            OperationStatusRest status = experimentResultService.storeExperimentToExperimentResult(execContext.asSimple(), taskParamsYaml);
            if (status.status!=EnumsApi.OperationStatus.OK) {
                throw new InternalFunctionException(
                    new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, status.getErrorMessagesAsStr()));
            }
        } catch (Throwable th) {
            throw new InternalFunctionException(
                new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, th.getMessage()));
        }
    }
}

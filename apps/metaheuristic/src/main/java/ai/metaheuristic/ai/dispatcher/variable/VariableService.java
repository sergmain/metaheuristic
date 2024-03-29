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

package ai.metaheuristic.ai.dispatcher.variable;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.*;

/**
 * @author Serge
 * Date: 9/28/2020
 * Time: 11:00 PM
 */
@SuppressWarnings("DuplicatedCode")
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class VariableService {

    private final VariableTxService variableTxService;
    private final VariableRepository variableRepository;
    private final ExecContextCache execContextCache;
    private final InternalFunctionVariableService internalFunctionVariableService;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void createInputVariablesForSubProcess(
            VariableData.VariableDataSource variableDataSource,
            Long execContextId, String inputVariableName,
            String currTaskContextId, boolean contentAsArray) {
        TxUtils.checkTxNotExists();
        variableTxService.createInputVariablesForSubProcess(variableDataSource, execContextId, inputVariableName, currTaskContextId, contentAsArray);
    }

    @Nullable
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Long variableBlobIdRef(Long variableId) {
        Variable v = variableRepository.findById(variableId).orElse(null);
        return v==null ? null : v.variableBlobId;
    }

    public List<String> getFilenameByVariableAndExecContextId(Long execContextId, String variable) {
        return variableRepository.findFilenameByVariableAndExecContextId(execContextId, variable);
    }

    public void checkFinalOutputVariables(TaskParamsYaml taskParamsYaml, Long subExecContextId) {
        ExecContextImpl execContext = execContextCache.findById(subExecContextId, true);
        if (execContext == null) {
            throw new InternalFunctionException(exec_context_not_found, "992.300 ExecContext Not found");
        }
        ExecContextParamsYaml ecpy = execContext.getExecContextParamsYaml();
        try {
            for (int i = 0; i < taskParamsYaml.task.outputs.size(); i++) {
                ExecContextParamsYaml.Variable execContextOutput = ecpy.variables.outputs.get(i);
                List<VariableUtils.VariableHolder> holders = internalFunctionVariableService.discoverVariables(
                        subExecContextId, Consts.TOP_LEVEL_CONTEXT_ID, execContextOutput.name);
                if (holders.size() > 1) {
                    throw new InternalFunctionException(source_code_is_broken,
                            "992.340 Too many variables with the same name at top-level context, name: " + execContextOutput.name);
                }

                VariableUtils.VariableHolder variableHolder = holders.get(0);
                if (variableHolder.variable == null) {
                    throw new InternalFunctionException(variable_not_found, "992.360 local variable with name: " + execContextOutput.name + " wasn't found");
                }
                Variable sv = variableTxService.getVariable(variableHolder.variable.id);
                if (sv==null) {
                    throw new InternalFunctionException(variable_not_found,
                            S.f("992.300 variable %s in execContext #%d wasn't found", variableHolder.variable.name, subExecContextId));
                }
            }
        } catch (InternalFunctionException e) {
            throw e;
        } catch (Throwable th) {
            String es = "992.400 error: " + th.getMessage();
            log.error(es, th);
            throw new InternalFunctionException(system_error, es);
        }
    }
}

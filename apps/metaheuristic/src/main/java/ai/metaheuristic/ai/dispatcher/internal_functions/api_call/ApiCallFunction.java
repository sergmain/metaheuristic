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
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextVariableService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.mhbp.beans.Api;
import ai.metaheuristic.ai.mhbp.provider.ProviderData;
import ai.metaheuristic.ai.mhbp.provider.ProviderQueryService;
import ai.metaheuristic.ai.mhbp.repositories.ApiRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.*;
import static ai.metaheuristic.ai.mhbp.scenario.ScenarioUtils.getNameForVariable;
import static ai.metaheuristic.ai.mhbp.scenario.ScenarioUtils.getVariables;
import static ai.metaheuristic.api.EnumsApi.OperationStatus.OK;

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

    public static final String PROMPT = "prompt";
    public static final String API_CODE = "apiCode";

    private final InternalFunctionVariableService internalFunctionVariableService;
    private final ExecContextVariableService execContextVariableService;
    private final VariableService variableService;
    private final GlobalVariableService globalVariableService;
    private final ProviderQueryService providerQueryService;
    private final ApiRepository apiRepository;

    @Override
    public String getCode() {
        return Consts.MH_API_CALL_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_API_CALL_FUNCTION;
    }

    @SneakyThrows
    @Override
    public void process(
            ExecContextData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId,
            TaskParamsYaml taskParamsYaml) {

        TxUtils.checkTxNotExists();

        if (taskParamsYaml.task.outputs.isEmpty()) {
            throw new InternalFunctionException(variable_not_found, "#513.380 output variable not found for task #"+taskId);
        }

        String apiCode = MetaUtils.getValue(taskParamsYaml.task.metas, API_CODE);
        if (S.b(apiCode)) {
            throw new InternalFunctionException(meta_not_found, "#513.300 meta '"+ API_CODE +"' wasn't found or it's blank");
        }
        Api api = apiRepository.findByApiCode(apiCode);
        if (api==null) {
            throw new InternalFunctionException(general_error, "#513.300 API wasn't found with code '"+ PROMPT +"' wasn't found or it's blank");
        }

        String prompt = MetaUtils.getValue(taskParamsYaml.task.metas, PROMPT);
        if (S.b(prompt)) {
            throw new InternalFunctionException(meta_not_found, "#513.300 meta '"+ PROMPT +"' wasn't found or it's blank");
        }

        final List<String> variables = getVariables(prompt, false);

        for (String variable : variables) {
            String varName = getNameForVariable(variable);
            String value = getValueOfVariable(simpleExecContext.execContextId, taskContextId, varName);
            if (value==null) {
                throw new InternalFunctionException(data_not_found, "#513.340 data wasn't found, variable: "+variable+", normalized: " + varName);
            }
            prompt = StringUtils.replaceEach(prompt, new String[]{"[["+variable+"]]", "{{"+variable+"}}"}, new String[]{value, value});
        }

        ProviderData.QueriedData queriedData = new ProviderData.QueriedData(prompt, null);
        ProviderData.QuestionAndAnswer answer = providerQueryService.processQuery(api, queriedData, ProviderQueryService::asQueriedInfoWithError);
        if (answer.status()!=OK || S.b(answer.a())) {
            throw new InternalFunctionException(data_not_found, "#513.340 API call error: "+answer.error()+", prompt: " + prompt+", answer: " + answer.a());
        }

        TaskParamsYaml.OutputVariable outputVariable = taskParamsYaml.task.outputs.get(0);
        VariableSyncService.getWithSyncVoid(outputVariable.id, ()->execContextVariableService.storeStringInVariable(outputVariable, answer.a()));

        //noinspection unused
        int i=0;
    }

    private String getValueOfVariable(Long execContextId, String taskContextId, String inputVariableName) {
        List<VariableUtils.VariableHolder> holders = internalFunctionVariableService.discoverVariables(execContextId, taskContextId, inputVariableName);
        if (holders.size()>1) {
            throw new InternalFunctionException(
                    new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, "#995.040 Too many variables"));
        }

        VariableUtils.VariableHolder variableHolder = holders.get(0);
        String s;
        if (variableHolder.variable!=null) {
            s = variableService.getVariableDataAsString(variableHolder.variable.id);
        }
        else if (variableHolder.globalVariable!=null) {
            s = globalVariableService.getVariableDataAsString(variableHolder.globalVariable.id);
        }
        else {
            throw new IllegalStateException("variableHolder.variabl==null && variableHolder.globalVariable==null");
        }
        return s;
    }
}

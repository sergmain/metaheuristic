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

package ai.metaheuristic.ai.dispatcher.internal_functions.api_call;

import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.mhbp.beans.Api;
import ai.metaheuristic.ai.mhbp.provider.ProviderData;
import ai.metaheuristic.ai.mhbp.provider.ProviderQueryService;
import ai.metaheuristic.ai.mhbp.repositories.ApiRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.*;
import static ai.metaheuristic.ai.mhbp.scenario.ScenarioUtils.getNameForVariable;
import static ai.metaheuristic.ai.mhbp.scenario.ScenarioUtils.getVariables;
import static ai.metaheuristic.api.EnumsApi.OperationStatus.OK;

/**
 * @author Sergio Lissner
 * Date: 5/23/2023
 * Time: 11:46 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ApiCallService {

    public static final String PROMPT = "prompt";
    public static final String API_CODE = "apiCode";
    public static final String RAW_OUTPUT = "rawOutput";

    private final InternalFunctionVariableService internalFunctionVariableService;
    private final VariableTxService variableTxService;
    private final ProviderQueryService providerQueryService;
    private final ApiRepository apiRepository;

    public ProviderData.QuestionAndAnswer callApi(ExecContextData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId, TaskParamsYaml taskParamsYaml) {
        TxUtils.checkTxNotExists();

        if (taskParamsYaml.task.outputs.isEmpty()) {
            throw new InternalFunctionException(variable_not_found, "513.040 output variable not found for task #" + taskId);
        }

        String apiCode = MetaUtils.getValue(taskParamsYaml.task.metas, API_CODE);
        if (S.b(apiCode)) {
            throw new InternalFunctionException(meta_not_found, "513.080 meta '"+ API_CODE +"' wasn't found or it's blank");
        }
        Api api = apiRepository.findByApiCode(apiCode);
        if (api==null) {
            throw new InternalFunctionException(general_error, "513.120 API wasn't found with code '"+ PROMPT +"' wasn't found or it's blank");
        }

        String prompt = MetaUtils.getValue(taskParamsYaml.task.metas, PROMPT);
        if (S.b(prompt)) {
            throw new InternalFunctionException(meta_not_found, "513.160 meta '"+ PROMPT +"' wasn't found or it's blank");
        }

        final List<String> variables = getVariables(prompt, false);

        for (String variable : variables) {
            String varName = getNameForVariable(variable);
            String value = internalFunctionVariableService.getValueOfVariable(simpleExecContext.execContextId, taskContextId, varName);
            if (value==null) {
                throw new InternalFunctionException(data_not_found, "513.200 data wasn't found, variable: "+variable+", normalized: " + varName);
            }
            prompt = StringUtils.replaceEach(prompt, new String[]{"[[" + variable + "]]", "{{" + variable + "}}"}, new String[]{value, value});
        }
        log.info("513.240 task #{}, prompt: {}", taskId, prompt);
        ProviderData.QueriedData queriedData = new ProviderData.QueriedData(prompt, simpleExecContext.asUserExecContext());
        ProviderData.QuestionAndAnswer answer = providerQueryService.processQuery(api, queriedData, ProviderQueryService::asQueriedInfoWithError);
        if (answer.status()!=OK) {
            throw new InternalFunctionException(data_not_found, "513.280 API call error: "+answer.error()+", prompt: " + prompt);
        }
        if (answer.a()==null) {
            throw new InternalFunctionException(data_not_found, "513.320 answer.a() is null, error: "+answer.error()+", prompt: " + prompt);
        }

        if (answer.a().processedAnswer.rawAnswerFromAPI().type().binary) {
            if (answer.a().processedAnswer.rawAnswerFromAPI().bytes()==null) {
                throw new InternalFunctionException(data_not_found, "513.340 processedAnswer.rawAnswerFromAPI().bytes() is null, error: "+answer.error()+", prompt: " + prompt);
            }
            TaskParamsYaml.OutputVariable outputVariable = taskParamsYaml.task.outputs.get(0);
            // right now, only image is supported
//            final EnumsApi.VariableType variableType = v.getDataStorageParams().type;
//            EnumsApi.VariableType type = variableType==null ? EnumsApi.VariableType.unknown : variableType;
            EnumsApi.VariableType type = EnumsApi.VariableType.image;

            VariableSyncService.getWithSyncVoid(outputVariable.id,
                    ()-> variableTxService.storeBytesInVariable(outputVariable, answer.a().processedAnswer.rawAnswerFromAPI().bytes(), type));
        }
        else {
            if (answer.a().processedAnswer.answer()==null) {
                throw new InternalFunctionException(data_not_found, "513.360 processedAnswer.answer() is null, error: "+answer.error()+", prompt: " + prompt);
            }
            log.info("513.380 task #{}, raw answer: {}", taskId, answer.a().processedAnswer.rawAnswerFromAPI().raw());
            log.info("513.385 task #{}, answer: {}", taskId, answer.a().processedAnswer.answer());

            String rawOutputVariableName = MetaUtils.getValue(taskParamsYaml.task.metas, RAW_OUTPUT);
            TaskParamsYaml.OutputVariable outputVariable = taskParamsYaml.task.outputs.stream().filter(o->!o.name.equals(rawOutputVariableName)).findFirst().orElse(null);
            if (outputVariable==null) {
                String names = taskParamsYaml.task.outputs.stream().map(TaskParamsYaml.OutputVariable::getName).collect(Collectors.joining(", "));
                throw new InternalFunctionException(output_variable_not_found, "513.345 nn-raw output variable wasn't fount, all outputs: " + names);
            }
            VariableSyncService.getWithSyncVoid(outputVariable.id,
                    () -> variableTxService.storeStringInVariable(simpleExecContext.execContextId, taskId, outputVariable, answer.a().processedAnswer.answer()));


            TaskParamsYaml.OutputVariable rawOutputVariable = taskParamsYaml.task.outputs.stream()
                    .filter(o1->o1.name.equals(rawOutputVariableName))
                    .findFirst()
                    .orElse(null);

            if (rawOutputVariable!=null) {
                if (S.b(answer.a().processedAnswer.rawAnswerFromAPI().raw())) {
                    VariableSyncService.getWithSyncVoid(rawOutputVariable.id,
                            () -> variableTxService.setVariableAsNull(taskId, rawOutputVariable.id));
                }
                else {
                    VariableSyncService.getWithSyncVoid(rawOutputVariable.id,
                            () -> variableTxService.storeStringInVariable(simpleExecContext.execContextId, taskId, rawOutputVariable, answer.a().processedAnswer.rawAnswerFromAPI().raw()));
                }
            }

        }
        return answer;
    }


}

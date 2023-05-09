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

package ai.metaheuristic.ai.dispatcher.internal_functions.string_as_variable;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.data.StringVariableData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextVariableService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.JsonUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.yaml.YamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.*;

/**
 * @author Serge
 * Date: 6/27/2021
 * Time: 11:20 AM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class StringAsVariableFunction implements InternalFunction {

    private static final String MAPPING = "mapping";

    private final InternalFunctionVariableService internalFunctionVariableService;
    private final ExecContextVariableService execContextVariableService;
    private final VariableService variableService;
    private final GlobalVariableService globalVariableService;

    @Override
    public String getCode() {
        return Consts.MH_STRING_AS_VARIABLE_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_STRING_AS_VARIABLE_FUNCTION;
    }

    @SneakyThrows
    @Override
    public void process(
            ExecContextData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId,
            TaskParamsYaml taskParamsYaml) {

        TxUtils.checkTxNotExists();

        if (taskParamsYaml.task.inline==null) {
            throw new InternalFunctionException(inline_not_found, "#513.200 inline is null");
        }

        String mappingStr = MetaUtils.getValue(taskParamsYaml.task.metas, MAPPING);
        if (S.b(mappingStr)) {
            throw new InternalFunctionException(meta_not_found, "#513.300 meta '"+ MAPPING +"' wasn't found or it's blank");
        }

        Yaml yaml = YamlUtils.init(StringVariableData.Mapping.class);
        StringVariableData.Mapping mapping = yaml.load(mappingStr);

        for (StringVariableData.StringAsVar inlineAsVar : mapping.mapping) {

            // DO NOT remove inlineAsVar.group
            //noinspection deprecation
            String keyName = inlineAsVar.key != null ? inlineAsVar.key : inlineAsVar.group;
            if (keyName==null) {
                throw new InternalFunctionException(source_code_is_broken, "#513.340 mapping must have a field key or group, "+ inlineAsVar);
            }
            final Map<String, String> map;
            Enums.StringAsVariableSource source = inlineAsVar.source==null ? Enums.StringAsVariableSource.inline : inlineAsVar.source;
            switch (source) {
                case inline:
                    map = taskParamsYaml.task.inline.get(keyName);
                    break;
                case variable:
                    String json = getValueOfVariable(simpleExecContext.execContextId, taskContextId, keyName);
                    Map<String, Object> mapObj = JsonUtils.getMapper().readValue(json, Map.class);
                    map = new HashMap<>();
                    mapObj.forEach((k,v)->map.put(k, v.toString()));
                    break;
                default:
                    throw new IllegalStateException("unknown source: " + source);
            }

            if (map==null) {
                throw new InternalFunctionException(data_not_found, "#513.340 data wasn't found, key: "+keyName+", source: "+source+"");
            }
            String value = map.get(inlineAsVar.name);
            if (value==null) {
                throw new InternalFunctionException(data_not_found, "#513.360 data for key '"+keyName+"' with name '"+inlineAsVar.name+"' wasn't found");
            }

            TaskParamsYaml.OutputVariable outputVariable = taskParamsYaml.task.outputs.stream()
                    .filter(o->o.name.equals(inlineAsVar.output))
                    .findFirst()
                    .orElseThrow(()->new InternalFunctionException(variable_not_found, "#513.380 output variable not found '"+inlineAsVar.output+"'"));

            VariableSyncService.getWithSyncVoid(outputVariable.id, ()->execContextVariableService.storeStringInVariable(outputVariable, value));
        }

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


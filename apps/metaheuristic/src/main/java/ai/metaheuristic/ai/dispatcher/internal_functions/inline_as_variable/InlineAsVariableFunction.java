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

package ai.metaheuristic.ai.dispatcher.internal_functions.inline_as_variable;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InlineVariableData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextVariableService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.yaml.YamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

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
public class InlineAsVariableFunction implements InternalFunction {

    private static final String MAPPING = "mapping";

    private final ExecContextVariableService execContextVariableService;

    @Override
    public String getCode() {
        return Consts.MH_INLINE_AS_VARIABLE_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_INLINE_AS_VARIABLE_FUNCTION;
    }

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

        Yaml yaml = YamlUtils.init(InlineVariableData.Mapping.class);
        InlineVariableData.Mapping mapping = yaml.load(mappingStr);

        for (InlineVariableData.InlineAsVar inlineAsVar : mapping.mapping) {

            final Map<String, String> map = taskParamsYaml.task.inline.get(inlineAsVar.group);
            if (map==null) {
                throw new InternalFunctionException(inline_not_found, "#513.340 inline group '"+inlineAsVar.group+"' wasn't found");
            }
            String value = map.get(inlineAsVar.name);
            if (value==null) {
                throw new InternalFunctionException(inline_not_found, "#513.360 inline for group '"+inlineAsVar.group+"' with name '"+inlineAsVar.name+"' wasn't found");
            }

            TaskParamsYaml.OutputVariable outputVariable = taskParamsYaml.task.outputs.stream()
                    .filter(o->o.name.equals(inlineAsVar.output))
                    .findFirst()
                    .orElseThrow(()->new InternalFunctionException(variable_not_found, "#513.380 output variable not found '"+inlineAsVar.output+"'"));

            execContextVariableService.storeStringInVariable(outputVariable, value);
        }

        //noinspection unused
        int i=0;
    }
}


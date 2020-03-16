/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.dispatcher.internal_functions;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.internal_functions.permute_variables_and_hyper_params.PermuteVariablesAndHyperParamsFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.variable_splitter.VariableSplitterFunction;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ai.metaheuristic.ai.dispatcher.data.InternalFunctionData.InternalFunctionProcessingResult;

/**
 * @author Serge
 * Date: 1/17/2020
 * Time: 9:47 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class InternalFunctionProcessor {

    public final VariableSplitterFunction resourceSplitterFunction;
    public final PermuteVariablesAndHyperParamsFunction permuteVariablesAndHyperParamsFunction;
    public final SourceCodeCache sourceCodeCache;
    public final List<InternalFunction> internalFunctions;

    private final Map<String, InternalFunction> internalFunctionMap = new HashMap<>();

    @PostConstruct
    public void postConstruct() {
        internalFunctions.forEach(o->internalFunctionMap.put(o.getCode(), o));
    }
/*
    public void registerInternalFunction(InternalFunction internalFunction) {
        internalFunctionMap.put(internalFunction.getCode(), internalFunction);
    }
*/

    public boolean isRegistered(String functionCode) {
        return internalFunctionMap.containsKey(functionCode);
    }

    public InternalFunctionProcessingResult process(
            String functionCode, Long sourceCodeId, Long execContextId, String internalContextId, Map<String, List<String>> inputResourceIds) {
        InternalFunction internalFunction = internalFunctionMap.get(functionCode);
        if (internalFunction==null) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.function_not_found);
        }

        SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode==null) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.source_code_not_found);
        }

        SourceCodeParamsYaml scpy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(sourceCode.getSourceCodeStoredParamsYaml().source);
        return internalFunction.process(sourceCodeId, execContextId, internalContextId, scpy.source.variables, inputResourceIds);
    }
}

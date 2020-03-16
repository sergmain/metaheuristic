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

package ai.metaheuristic.ai.dispatcher.internal_functions.permute_variables_and_hyper_params;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionProcessor;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.dispatcher.data.InternalFunctionData.*;

/**
 * @author Serge
 * Date: 2/1/2020
 * Time: 9:17 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class PermuteVariablesAndHyperParamsFunction implements InternalFunction {

    private final VariableRepository variableRepository;
/*
    private final InternalFunctionProcessor internalFunctionProcessor;

    @PostConstruct
    public void postConstruct() {
        internalFunctionProcessor.registerInternalFunction(this);
    }
*/

    @Override
    public String getCode() {
        return Consts.MH_PERMUTE_VARIABLES_AND_HYPER_PARAMS_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_PERMUTE_VARIABLES_AND_HYPER_PARAMS_FUNCTION;
    }

    public InternalFunctionProcessingResult process(
            Long sourceCodeId, Long execContextId, String internalContextId, SourceCodeParamsYaml.VariableDefinition variableDefinition,
            Map<String, List<String>> inputResourceIds) {

        List<String> values = inputResourceIds.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        if (values.size()>1) {
            throw new IllegalStateException("Too many input codes");
        }
        String inputCode = values.get(0);
        Variable bd = variableRepository.findById(Long.valueOf(inputCode)).orElse(null);
        if (bd==null) {
            throw new IllegalStateException("Variable not found for code " + inputCode);
        }
        if (true) {
            throw new NotImplementedException("not yet");
        }
        return Consts.INTERNAL_FUNCTION_PROCESSING_RESULT_OK;
    }

}

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

package ai.metaheuristic.ai.internal_function;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static ai.metaheuristic.ai.dispatcher.data.InternalFunctionData.InternalFunctionProcessingResult;

/**
 * @author Serge
 * Date: 3/14/2020
 * Time: 8:37 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class SimpleInternalFunctionForTest implements InternalFunction {

    public static final String MH_TEST_SIMPLE_INTERNAL_FUNCTION = "mh.test.simple-internal-function";

    @Override
    public String getCode() {
        return MH_TEST_SIMPLE_INTERNAL_FUNCTION;
    }

    @Override
    public String getName() {
        return MH_TEST_SIMPLE_INTERNAL_FUNCTION;
    }

    @Override
    public InternalFunctionProcessingResult process(Long sourceCodeId, Long execContextId, String internalContextId, SourceCodeParamsYaml.VariableDefinition variableDefinition, Map<String, List<String>> inputResourceIds) {


        return Consts.INTERNAL_FUNCTION_PROCESSING_RESULT_OK;
    }
}

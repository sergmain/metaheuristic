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

package ai.metaheuristic.ai.dispatcher.internal_functions.finish;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionProcessor;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

import static ai.metaheuristic.ai.dispatcher.data.InternalFunctionData.*;

/**
 * @author Serge
 * Date: 3/13/2020
 * Time: 11:13 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class FinishFunction implements InternalFunction {

    private final InternalFunctionProcessor internalFunctionProcessor;

    @PostConstruct
    public void postConstruct() {
        internalFunctionProcessor.registerInternalFunction(this);
    }

    @Override
    public String getCode() {
        return Consts.MH_FINISH_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_FINISH_FUNCTION;
    }

    @Override
    public InternalFunctionProcessingResult process(Long sourceCodeId, Long execContextId, String internalContextId, SourceCodeParamsYaml.VariableDefinition variableDefinition, Map<String, List<String>> inputResourceIds) {
        return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.ok);
    }
}

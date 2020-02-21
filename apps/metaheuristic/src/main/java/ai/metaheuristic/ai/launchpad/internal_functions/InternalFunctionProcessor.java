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

package ai.metaheuristic.ai.launchpad.internal_functions;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.launchpad.beans.SourceCodeImpl;
import ai.metaheuristic.ai.launchpad.internal_functions.permute_variables_and_hyper_params.PermuteVariablesAndHyperParamsFunction;
import ai.metaheuristic.ai.launchpad.internal_functions.resource_splitter.ResourceSplitterFunction;
import ai.metaheuristic.ai.launchpad.source_code.SourceCodeCache;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 1/17/2020
 * Time: 9:47 PM
 */
@Service
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class InternalFunctionProcessor {

    public final ResourceSplitterFunction resourceSplitterFunction;
    public final PermuteVariablesAndHyperParamsFunction permuteVariablesAndHyperParamsFunction;
    public final SourceCodeCache sourceCodeCache;

    public List<InternalFunctionOutput> process(
            String functionCode, Long sourceCodeId, Long execContextId, String internalContextId, Map<String, List<String>> inputResourceIds) {

        SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        SourceCodeParamsYaml scpy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(sourceCode.getSourceCodeStoredParamsYaml().source);

        switch(functionCode) {
            case Consts.MH_RESOURCE_SPLITTER_FUNCTION:
                return resourceSplitterFunction.process(sourceCodeId, execContextId, internalContextId, scpy.source.variables, inputResourceIds);
            case Consts.MH_PERMUTE_VARIABLES_AND_HYPER_PARAMS_FUNCTION:
                return permuteVariablesAndHyperParamsFunction.process(sourceCodeId, execContextId, internalContextId, scpy.source.variables, inputResourceIds);
            default:
                throw new IllegalStateException("Unknown internal function: " + functionCode);
        }
    }
}

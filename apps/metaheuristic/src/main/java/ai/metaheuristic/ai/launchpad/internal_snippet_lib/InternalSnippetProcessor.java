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

package ai.metaheuristic.ai.launchpad.internal_snippet_lib;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.launchpad.beans.SourceCodeImpl;
import ai.metaheuristic.ai.launchpad.internal_snippet_lib.permute_variables_and_hyper_params.PermuteVariablesAndHyperParamsSnippet;
import ai.metaheuristic.ai.launchpad.internal_snippet_lib.resource_splitter.ResourceSplitterSnippet;
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
public class InternalSnippetProcessor {

    public final ResourceSplitterSnippet resourceSplitterSnippet;
    public final PermuteVariablesAndHyperParamsSnippet permuteVariablesAndHyperParamsSnippet;
    public final SourceCodeCache sourceCodeCache;

    public List<InternalSnippetOutput> process(
            String snippetCode, Long sourceCodeId, Long execContextId, String internalContextId, Map<String, List<String>> inputResourceIds) {

        SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        SourceCodeParamsYaml scpy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(sourceCode.getSourceCodeStoredParamsYaml().source);

        switch(snippetCode) {
            case Consts.MH_RESOURCE_SPLITTER_SNIPPET:
                return resourceSplitterSnippet.process(sourceCodeId, execContextId, internalContextId, scpy.source.variables, inputResourceIds);
            case Consts.MH_PERMUTE_VARIABLES_AND_HYPER_PARAMS_SNIPPET:
                return permuteVariablesAndHyperParamsSnippet.process(sourceCodeId, execContextId, internalContextId, scpy.source.variables, inputResourceIds);
            default:
                throw new IllegalStateException("Unknown internal snippet: " + snippetCode);
        }
    }
}

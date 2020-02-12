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

package ai.metaheuristic.ai.launchpad.internal_snippet_lib.permute_variables_and_hyper_params;

import ai.metaheuristic.ai.launchpad.beans.Variable;
import ai.metaheuristic.ai.launchpad.internal_snippet_lib.InternalSnippet;
import ai.metaheuristic.ai.launchpad.internal_snippet_lib.InternalSnippetOutput;
import ai.metaheuristic.ai.launchpad.repositories.VariableRepository;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 2/1/2020
 * Time: 9:17 PM
 */
@Service
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class PermuteVariablesAndHyperParamsSnippet implements InternalSnippet {

    private final VariableRepository variableRepository;

    public List<InternalSnippetOutput> process(
            Long planId, Long workbookId, String contextId, SourceCodeParamsYaml.VariableDefinition variableDefinition,
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
        return null;
    }

}

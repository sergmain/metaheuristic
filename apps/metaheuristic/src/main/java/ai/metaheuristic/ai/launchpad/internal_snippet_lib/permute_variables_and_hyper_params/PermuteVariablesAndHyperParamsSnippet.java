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

import ai.metaheuristic.ai.launchpad.beans.BinaryData;
import ai.metaheuristic.ai.launchpad.internal_snippet_lib.InternalSnippet;
import ai.metaheuristic.ai.launchpad.repositories.BinaryDataRepository;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
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

    private final BinaryDataRepository binaryDataRepository;

    public void process(Long planId, Long workbookId, String contextId, TaskParamsYaml taskParamsYaml) {

        List<String> values = taskParamsYaml.taskYaml.inputResourceIds.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        if (values.size()>1) {
            throw new IllegalStateException("Too many input codes");
        }
        String inputCode = values.get(0);
        BinaryData bd = binaryDataRepository.findById(Long.valueOf(inputCode)).orElse(null);
        if (bd==null) {
            throw new IllegalStateException("BinaryData not found for code " + inputCode);
        }
    }

}

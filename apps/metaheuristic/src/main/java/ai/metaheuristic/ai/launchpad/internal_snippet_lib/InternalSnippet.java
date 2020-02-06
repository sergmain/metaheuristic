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

import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;

import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 2/1/2020
 * Time: 9:19 PM
 */
public interface InternalSnippet {
    List<InternalSnippetOutput> process(
            Long planId, Long workbookId, String contxtId, PlanParamsYaml.VariableDefinition variableDefinition,
            Map<String, List<String>> inputResourceIds);
}

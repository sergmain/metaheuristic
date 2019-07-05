/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.plan;

import ai.metaheuristic.ai.launchpad.snippet.SnippetService;
import ai.metaheuristic.ai.yaml.plan.PlanParamsYamlUtils;
import ai.metaheuristic.api.launchpad.process.SnippetDefForPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Profile("launchpad")
@Service
@RequiredArgsConstructor
public class CommonProcessValidatorService {

    private final SnippetService snippetService;

    public boolean checkRequiredVersion(int planParamsVersion, SnippetDefForPlan snDef) {
        int taskParamsYamlVersion = PlanParamsYamlUtils.getRequiredVertionOfTaskParamsYaml(planParamsVersion);
        boolean ok = snippetService.isSnippetVersionOk(taskParamsYamlVersion, snDef);
        if (!ok) {
            log.error("#175.030 Version of snippet {} is too low, required version: {}", snDef.code, taskParamsYamlVersion);
            return false;
        }
        return true;
    }
}

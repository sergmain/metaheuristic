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

package ai.metaheuristic.ai.launchpad.file_process;

import ai.metaheuristic.ai.launchpad.plan.ProcessValidator;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.launchpad.Plan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class FileProcessValidator implements ProcessValidator {

    @Override
    public EnumsApi.PlanValidateStatus validate(Plan plan, PlanParamsYaml.Process process, boolean isFirst) {
        if (process.snippet==null) {
            return EnumsApi.PlanValidateStatus.SNIPPET_NOT_DEFINED_ERROR;
        }
        boolean isInternal = (process.snippet.context== EnumsApi.SnippetExecContext.internal);
        if (isInternal) {
            for (PlanParamsYaml.Variable variable : process.output) {
                if (variable.sourcing!= EnumsApi.DataSourcing.launchpad) {
                    return EnumsApi.PlanValidateStatus.INTERNAL_SNIPPET_SUPPORT_ONLY_LAUNCHPAD_ERROR;
                }
            }
            if (CollectionUtils.isNotEmpty(process.preSnippets)) {
                return EnumsApi.PlanValidateStatus.PRE_SNIPPET_WITH_INTERNAL_SNIPPET_ERROR;
            }
            if (CollectionUtils.isNotEmpty(process.postSnippets)) {
                return EnumsApi.PlanValidateStatus.POST_SNIPPET_WITH_INTERNAL_SNIPPET_ERROR;
            }
        }
        return null;
    }

}

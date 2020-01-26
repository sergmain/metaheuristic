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
        if (process.getSnippets() == null || process.getSnippets().isEmpty()) {
            return EnumsApi.PlanValidateStatus.SNIPPET_NOT_DEFINED_ERROR;
        }
        if (!process.parallelExec && process.snippets.size()>1) {
            return EnumsApi.PlanValidateStatus.TOO_MANY_SNIPPET_CODES_ERROR;
        }
        boolean isInternal = process.snippets.stream().anyMatch(s->s.context== EnumsApi.SnippetExecContext.internal);
        if (isInternal) {
            if (process.snippets.size()>1) {
                return EnumsApi.PlanValidateStatus.TOO_MANY_INTERNAL_SNIPPETS_ERROR;
            }
            if (process.outputParams.sourcing!= EnumsApi.DataSourcing.launchpad) {
                return EnumsApi.PlanValidateStatus.INTERNAL_SNIPPET_SUPPORT_ONLY_LAUNCHPAD_ERROR;
            }
            if (CollectionUtils.isNotEmpty(process.preSnippets)) {
                return EnumsApi.PlanValidateStatus.PRE_SNIPPET_WITH_INTERNAL_SNIPPET_ERROR;
            }
            if (CollectionUtils.isNotEmpty(process.postSnippets)) {
                return EnumsApi.PlanValidateStatus.POST_SNIPPET_WITH_INTERNAL_SNIPPET_ERROR;
            }
            if (process.parallelExec) {
                return EnumsApi.PlanValidateStatus.INTERNAL_SNIPPET_WITH_PARALLEL_EXEC_ERROR;
            }
            if (process.snippets.stream().anyMatch(s -> s.context == EnumsApi.SnippetExecContext.external)) {
                return EnumsApi.PlanValidateStatus.INTERNAL_AND_EXTERNAL_SNIPPET_IN_THE_SAME_PROCESS_ERROR;
            }
        }
        return null;
    }

}

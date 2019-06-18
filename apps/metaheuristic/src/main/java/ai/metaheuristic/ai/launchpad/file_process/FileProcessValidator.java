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

import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.launchpad.process.Process;
import ai.metaheuristic.api.v1.launchpad.Plan;
import ai.metaheuristic.ai.launchpad.beans.Snippet;
import ai.metaheuristic.ai.launchpad.plan.ProcessValidator;
import ai.metaheuristic.ai.launchpad.repositories.SnippetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Profile("launchpad")
public class FileProcessValidator implements ProcessValidator {

    private final SnippetRepository snippetRepository;

    public FileProcessValidator(SnippetRepository snippetRepository) {
        this.snippetRepository = snippetRepository;
    }

    @Override
    public EnumsApi.PlanValidateStatus validate(Plan plan, Process process, boolean isFirst) {
        if (process.getSnippetCodes() == null || process.getSnippetCodes().isEmpty()) {
            return EnumsApi.PlanValidateStatus.SNIPPET_NOT_DEFINED_ERROR;
        }
        for (String snippetCode : process.snippetCodes) {
            Snippet snippet = snippetRepository.findByCode(snippetCode);
            if (snippet==null) {
                log.error("#175.07 Snippet wasn't found for code: {}, process: {}", snippetCode, process);
                return EnumsApi.PlanValidateStatus.SNIPPET_NOT_FOUND_ERROR;
            }
        }
        if (process.preSnippetCode!=null) {
            for (String snippetCode : process.preSnippetCode) {
                Snippet snippet = snippetRepository.findByCode(snippetCode);
                if (snippet==null) {
                    log.error("#175.09 Pre-snippet wasn't found for code: {}, process: {}", snippetCode, process);
                    return EnumsApi.PlanValidateStatus.SNIPPET_NOT_FOUND_ERROR;
                }
            }
        }
        if (process.postSnippetCode!=null) {
            for (String snippetCode : process.postSnippetCode) {
                Snippet snippet = snippetRepository.findByCode(snippetCode);
                if (snippet == null) {
                    log.error("#175.11 Post-snippet wasn't found for code: {}, process: {}", snippetCode, process);
                    return EnumsApi.PlanValidateStatus.SNIPPET_NOT_FOUND_ERROR;
                }
            }
        }

        if (!process.parallelExec && process.snippetCodes.size()>1) {
            return EnumsApi.PlanValidateStatus.TOO_MANY_SNIPPET_CODES_ERROR;
        }

        return null;
    }
}

/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.experiment;

import aiai.ai.launchpad.beans.*;
import aiai.ai.launchpad.repositories.SnippetRepository;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.launchpad.Plan;
import ai.metaheuristic.api.v1.launchpad.Process;
import aiai.ai.launchpad.plan.ProcessValidator;
import aiai.ai.launchpad.repositories.ExperimentRepository;
import aiai.ai.launchpad.repositories.WorkbookRepository;
import aiai.ai.launchpad.snippet.SnippetService;
import ai.metaheuristic.api.v1.launchpad.Workbook;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("launchpad")
@Slf4j
public class ExperimentProcessValidator implements ProcessValidator {

    private final SnippetRepository snippetRepository;
    private final SnippetService snippetService;
    private final ExperimentRepository experimentRepository;
    private final WorkbookRepository workbookRepository;

    public ExperimentProcessValidator(SnippetRepository snippetRepository, SnippetService snippetService, ExperimentRepository experimentRepository, WorkbookRepository workbookRepository) {
        this.snippetRepository = snippetRepository;
        this.snippetService = snippetService;
        this.experimentRepository = experimentRepository;
        this.workbookRepository = workbookRepository;
    }

    // TODO experiment has to be stateless and have its own instances
    // TODO 2019.05.02 do we need an experiment to have its own instance still?

    @Override
    public EnumsApi.PlanValidateStatus validate(Plan plan, Process process, boolean isFirst) {
        if (process.snippetCodes!=null && process.snippetCodes.size() > 0) {
            return EnumsApi.PlanValidateStatus.SNIPPET_ALREADY_PROVIDED_BY_EXPERIMENT_ERROR;
        }
        if (StringUtils.isBlank(process.code)) {
            return EnumsApi.PlanValidateStatus.SNIPPET_NOT_DEFINED_ERROR;
        }
        if (StringUtils.isNotBlank(process.preSnippetCode)) {
            Snippet snippet = snippetRepository.findByCode(process.preSnippetCode);
            if (snippet==null) {
                log.error("#177.09 Pre-snippet wasn't found for code: {}, process: {}", process.preSnippetCode, process);
                return EnumsApi.PlanValidateStatus.SNIPPET_NOT_FOUND_ERROR;
            }
        }
        if (StringUtils.isNotBlank(process.postSnippetCode)) {
            Snippet snippet = snippetRepository.findByCode(process.postSnippetCode);
            if (snippet==null) {
                log.error("#177.11 Post-snippet wasn't found for code: {}, process: {}", process.postSnippetCode, process);
                return EnumsApi.PlanValidateStatus.SNIPPET_NOT_FOUND_ERROR;
            }
        }
        Experiment e = experimentRepository.findByCode(process.code);
        if (e==null) {
            return EnumsApi.PlanValidateStatus.EXPERIMENT_NOT_FOUND_ERROR;
        }
        if (e.getWorkbookId()!=null) {
            Workbook workbook = workbookRepository.findById(e.getWorkbookId()).orElse(null);
            if (workbook != null) {
                if (!plan.getId().equals(workbook.getPlanId())) {
                    return EnumsApi.PlanValidateStatus.EXPERIMENT_ALREADY_STARTED_ERROR;
                }
            }
            else {
                return EnumsApi.PlanValidateStatus.WORKBOOK_DOESNT_EXIST_ERROR;
            }
        }
        List<ExperimentSnippet> experimentSnippets = snippetService.getTaskSnippetsForExperiment(e.getId());
        if (experimentSnippets==null || experimentSnippets.size()<2) {
            return EnumsApi.PlanValidateStatus.EXPERIMENT_HASNT_ALL_SNIPPETS_ERROR;
        }

        if (!isFirst) {
            if (process.metas == null || process.metas.isEmpty()) {
                return EnumsApi.PlanValidateStatus.EXPERIMENT_META_NOT_FOUND_ERROR;
            }

            Process.Meta m1 = process.getMeta("dataset");
            if (m1 == null || StringUtils.isBlank(m1.getValue())) {
                return EnumsApi.PlanValidateStatus.EXPERIMENT_META_DATASET_NOT_FOUND_ERROR;
            }

            // TODO 2019.05.02 do we need this check?
//            Process.Meta m2 = process.getMeta("assembled-raw");
//            if (m2 == null || StringUtils.isBlank(m2.getValue())) {
//                return EnumsApi.PlanValidateStatus.EXPERIMENT_META_ASSEMBLED_RAW_NOT_FOUND_ERROR;
//            }

            Process.Meta m3 = process.getMeta("feature");
            if (m3 == null || StringUtils.isBlank(m3.getValue())) {
                return EnumsApi.PlanValidateStatus.EXPERIMENT_META_FEATURE_NOT_FOUND_ERROR;
            }
        }
        return null;
    }
}

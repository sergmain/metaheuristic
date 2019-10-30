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

package ai.metaheuristic.ai.launchpad.workbook;

import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.ai.launchpad.plan.PlanCache;
import ai.metaheuristic.ai.launchpad.plan.PlanUtils;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.yaml.workbook.WorkbookParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.plan.PlanApiData;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 7/4/2019
 * Time: 3:56 PM
 */
@Slf4j
@Profile("launchpad")
@Service
@RequiredArgsConstructor
public class WorkbookTopLevelService {

    private final WorkbookRepository workbookRepository;
    private final WorkbookService workbookService;
    private final PlanCache planCache;

    public PlanApiData.WorkbooksResult getWorkbooksOrderByCreatedOnDesc(Long planId, Pageable pageable, LaunchpadContext context) {
        return workbookService.getWorkbooksOrderByCreatedOnDescResult(planId, pageable, context);
    }

    public PlanApiData.TaskProducingResult createWorkbook(Long planId, String inputResourceParam, LaunchpadContext context) {
        final PlanApiData.TaskProducingResultComplex result = workbookService.createWorkbook(planId, PlanUtils.parseToWorkbookParamsYaml(inputResourceParam));
        return new PlanApiData.TaskProducingResult(
                result.getStatus()== EnumsApi.TaskProducingStatus.OK
                        ? new ArrayList<>()
                        : List.of("Error of creating workbook, " +
                        "validation status: " + result.getPlanValidateStatus()+", producing status: " + result.getPlanProducingStatus()),
                result.planValidateStatus,
                result.planProducingStatus,
                result.workbook.getId()
        );
    }

    public PlanApiData.WorkbookResult getWorkbookExtendedForDeletion(Long workbookId, LaunchpadContext context) {
        PlanApiData.WorkbookResult result = workbookService.getWorkbookExtended(workbookId);

        // don't show actual graph for this workbook
        WorkbookParamsYaml wpy = WorkbookParamsYamlUtils.BASE_YAML_UTILS.to(result.workbook .getParams());
        wpy.graph = null;
        result.workbook .setParams( WorkbookParamsYamlUtils.BASE_YAML_UTILS.toString(wpy) );

        return result;
    }

    public PlanApiData.WorkbookResult getWorkbookExtended(Long workbookId) {
        //noinspection UnnecessaryLocalVariable
        PlanApiData.WorkbookResult result = workbookService.getWorkbookExtended(workbookId);
        return result;
    }

}

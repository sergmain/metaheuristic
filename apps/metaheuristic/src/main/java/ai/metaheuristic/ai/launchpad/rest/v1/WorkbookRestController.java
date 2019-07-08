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

package ai.metaheuristic.ai.launchpad.rest.v1;

import ai.metaheuristic.ai.launchpad.plan.PlanTopLevelService;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookTopLevelService;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.plan.PlanApiData;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

/**
 * @author Serge
 * Date: 7/4/2019
 * Time: 3:55 PM
 */

// all urls in "/rest/v1/launchpad/plan" because of angular.
// need change angular code too but not know
@RequestMapping("/rest/v1/launchpad/plan")
@RestController
@Profile("launchpad")
@CrossOrigin
@RequiredArgsConstructor
public class WorkbookRestController {

    private final PlanTopLevelService planTopLevelService;
    private final WorkbookTopLevelService workbookTopLevelService;

    @GetMapping("/workbooks/{id}")
    public PlanApiData.WorkbooksResult workbooks(@PathVariable Long id, @PageableDefault(size = 5) Pageable pageable) {
        return workbookTopLevelService.getWorkbooksOrderByCreatedOnDesc(id, pageable);
    }

    @PostMapping("/workbook-add-commit")
    public PlanApiData.WorkbookResult workbookAddCommit(Long planId, String poolCode, String inputResourceParams) {
        //noinspection UnnecessaryLocalVariable
        PlanApiData.WorkbookResult workbookResult = planTopLevelService.addWorkbook(planId, poolCode, inputResourceParams);
        return workbookResult;
    }

    @PostMapping("/workbook-create")
    public PlanApiData.TaskProducingResult createWorkbook(Long planId, String inputResourceParam) {
        return workbookTopLevelService.createWorkbook(planId, inputResourceParam);
    }

    @GetMapping(value = "/workbook/{planId}/{workbookId}")
    public PlanApiData.WorkbookResult workbookEdit(@SuppressWarnings("unused") @PathVariable Long planId, @PathVariable Long workbookId) {
        return workbookTopLevelService.getWorkbookExtended(workbookId);
    }

    @PostMapping("/workbook-delete-commit")
    public OperationStatusRest workbookDeleteCommit(Long planId, Long workbookId) {
        return planTopLevelService.deleteWorkbookById(workbookId);
    }

    @GetMapping("/workbook-target-exec-state/{planId}/{state}/{id}")
    public OperationStatusRest workbookTargetExecState(@SuppressWarnings("unused") @PathVariable Long planId, @PathVariable String state, @PathVariable Long id) {
        return planTopLevelService.changeWorkbookExecState(state, id);
    }

}

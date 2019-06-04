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

import ai.metaheuristic.api.v1.launchpad.Plan;
import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.api.v1.data.PlanApiData;
import ai.metaheuristic.api.v1.data.OperationStatusRest;
import ai.metaheuristic.ai.launchpad.plan.PlanTopLevelService;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rest/v1/launchpad/plan")
@Profile("launchpad")
@CrossOrigin
//@CrossOrigin(origins="*", maxAge=3600)
public class PlanRestController {

    private final PlanTopLevelService planTopLevelService;

    public PlanRestController(PlanTopLevelService planTopLevelService) {
        this.planTopLevelService = planTopLevelService;
    }

    // ============= Plan =============

    @GetMapping("/plans")
    public PlanApiData.PlansResult plans(@PageableDefault(size = 5) Pageable pageable) {
        return planTopLevelService.getPlans(pageable);
    }

    @GetMapping(value = "/plan/{id}")
    public PlanApiData.PlanResult edit(@PathVariable Long id) {
        return planTopLevelService.getPlan(id);
    }

    @GetMapping(value = "/plan-validate/{id}")
    public PlanApiData.PlanResult validate(@PathVariable Long id) {
        return planTopLevelService.validatePlan(id);
    }

    @PostMapping("/plan-add-commit")
    public PlanApiData.PlanResult addFormCommit(@RequestBody PlanImpl plan, String planYamlAsStr) {
        return planTopLevelService.addPlan(plan, planYamlAsStr);
    }

    @PostMapping("/plan-edit-commit")
    public PlanApiData.PlanResult editFormCommit(@RequestBody Plan plan, String planYamlAsStr) {
        return planTopLevelService.updatePlan(plan, planYamlAsStr);
    }

    @PostMapping("/plan-delete-commit")
    public OperationStatusRest deleteCommit(Long id) {
        return planTopLevelService.deletePlanById(id);
    }

    // ============= Workbooks =============

    @GetMapping("/workbooks/{id}")
    public PlanApiData.WorkbooksResult workbooks(@PathVariable Long id, @PageableDefault(size = 5) Pageable pageable) {
        return planTopLevelService.getWorkbooksOrderByCreatedOnDesc(id, pageable);
    }

    @PostMapping("/workbook-add-commit")
    public PlanApiData.WorkbookResult workbookAddCommit(Long planId, String poolCode, String inputResourceParams) {
        //noinspection UnnecessaryLocalVariable
        PlanApiData.WorkbookResult workbookResult = planTopLevelService.addWorkbook(planId, poolCode, inputResourceParams);
        return workbookResult;
    }

    @PostMapping("/workbook-create")
    public PlanApiData.TaskProducingResult createWorkbook(Long planId, String inputResourceParam) {
        return planTopLevelService.createWorkbook(planId, inputResourceParam);
    }

    @GetMapping(value = "/workbook/{planId}/{workbookId}")
    public PlanApiData.WorkbookResult workbookEdit(@SuppressWarnings("unused") @PathVariable Long planId, @PathVariable Long workbookId) {
        return planTopLevelService.getWorkbookExtended(workbookId);
    }

    @PostMapping("/workbook-delete-commit")
    public OperationStatusRest workbookDeleteCommit(Long planId, Long workbookId) {
        return planTopLevelService.deleteWorkbookById(workbookId);
    }

    @GetMapping("/workbook-target-exec-state/{planId}/{state}/{id}")
    public OperationStatusRest workbookTargetExecState(@SuppressWarnings("unused") @PathVariable Long planId, @PathVariable String state, @PathVariable Long id) {
        return planTopLevelService.changeWorkbookExecState(state, id);
    }

    // ============= Service methods =============

    @GetMapping(value = "/emulate-producing-tasks/{workbookId}")
    public PlanApiData.TaskProducingResult emulateProducingTasks(@PathVariable Long workbookId) {
        return planTopLevelService.produceTasksWithoutPersistence(workbookId);
    }

    @GetMapping(value = "/create-all-tasks")
    public void createAllTasks() {
        planTopLevelService.createAllTasks();
    }

    @GetMapping(value = "/change-valid-status/{workbookId}/{status}")
    public OperationStatusRest changeValidStatus(@PathVariable Long workbookId, @PathVariable boolean status) {
        return planTopLevelService.changeValidStatus(workbookId, status);
    }



}

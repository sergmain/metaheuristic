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

import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.data.OperationStatusRest;
import ai.metaheuristic.api.v1.data.PlanApiData;
import ai.metaheuristic.api.v1.launchpad.Plan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Collections;

@SuppressWarnings("Duplicates")
@Controller
@RequestMapping("/launchpad/plan")
@Slf4j
@Profile("launchpad")
public class PlanController {

    private static final String REDIRECT_LAUNCHPAD_PLAN_PLANS = "redirect:/launchpad/plan/plans";

    private final PlanTopLevelService planTopLevelService;

    public PlanController(PlanTopLevelService planTopLevelService) {
        this.planTopLevelService = planTopLevelService;
    }

    @GetMapping("/plans")
    public String plans(Model model, @PageableDefault(size = 5) Pageable pageable,
                        @ModelAttribute("infoMessages") final ArrayList<String> infoMessages,
                        @ModelAttribute("errorMessage") final ArrayList<String> errorMessage) {
        PlanApiData.PlansResult plansResultRest = planTopLevelService.getPlans(pageable);
        ControllerUtils.addMessagesToModel(model, plansResultRest);
        model.addAttribute("result", plansResultRest);
        return "launchpad/plan/plans";
    }

    // for AJAX
    @PostMapping("/plans-part")
    public String plansPart(Model model, @PageableDefault(size = 10) Pageable pageable) {
        PlanApiData.PlansResult plansResultRest = planTopLevelService.getPlans(pageable);
        model.addAttribute("result", plansResultRest);
        return "launchpad/plan/plans :: table";
    }

    @GetMapping(value = "/plan-add")
    public String add(@ModelAttribute("plan") Plan plan) {
        return "launchpad/plan/plan-add";
    }

    @GetMapping(value = "/plan-edit/{id}")
    public String edit(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        PlanApiData.PlanResult planResultRest = planTopLevelService.getPlan(id);
        if (planResultRest.status== EnumsApi.PlanValidateStatus.PLAN_NOT_FOUND_ERROR) {
            redirectAttributes.addFlashAttribute("errorMessage", planResultRest.errorMessages);
            return REDIRECT_LAUNCHPAD_PLAN_PLANS;
        }
        model.addAttribute("plan", planResultRest.plan);
        model.addAttribute("planYamlAsStr", planResultRest.planYamlAsStr);
        return "launchpad/plan/plan-edit";
    }

    @GetMapping(value = "/plan-validate/{id}")
    public String validate(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        PlanApiData.PlanResult planResultRest = planTopLevelService.validatePlan(id);
        if (planResultRest.status== EnumsApi.PlanValidateStatus.PLAN_NOT_FOUND_ERROR) {
            redirectAttributes.addFlashAttribute("errorMessage", planResultRest.errorMessages);
            return REDIRECT_LAUNCHPAD_PLAN_PLANS;
        }

        model.addAttribute("plan", planResultRest.plan);
        model.addAttribute("planYamlAsStr", planResultRest.planYamlAsStr);
        model.addAttribute("infoMessages", planResultRest.infoMessages);
        model.addAttribute("errorMessage", planResultRest.errorMessages);
        return "launchpad/plan/plan-edit";
    }

    @PostMapping("/plan-add-commit")
    public String addFormCommit(Model model, PlanImpl plan, String planYamlAsStr, final RedirectAttributes redirectAttributes) {
        PlanApiData.PlanResult planResultRest = planTopLevelService.addPlan(plan, planYamlAsStr);
        if (planResultRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", planResultRest.errorMessages);
        }
        if (planResultRest.status==EnumsApi.PlanValidateStatus.OK ) {
            redirectAttributes.addFlashAttribute("infoMessages", Collections.singletonList("Validation result: OK"));
        }
        return REDIRECT_LAUNCHPAD_PLAN_PLANS;
    }

    @PostMapping("/plan-edit-commit")
    public String editFormCommit(Model model, Plan plan, String planYamlAsStr, final RedirectAttributes redirectAttributes) {
        PlanApiData.PlanResult planResultRest = planTopLevelService.updatePlan(plan, planYamlAsStr);
        if (planResultRest.isErrorMessages()) {
            model.addAttribute("errorMessage", planResultRest.errorMessages);
            return "redirect:/launchpad/plan/plan-edit/"+planResultRest.plan.getId();
        }

        if (planResultRest.status== EnumsApi.PlanValidateStatus.OK ) {
            redirectAttributes.addFlashAttribute("infoMessages", Collections.singletonList("Validation result: OK"));
        }
        return "redirect:/launchpad/plan/plan-edit/"+planResultRest.plan.getId();
    }

    @GetMapping("/plan-delete/{id}")
    public String delete(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        PlanApiData.PlanResult planResultRest = planTopLevelService.getPlan(id);
        if (planResultRest.status== EnumsApi.PlanValidateStatus.PLAN_NOT_FOUND_ERROR) {
            redirectAttributes.addFlashAttribute("errorMessage", planResultRest.errorMessages);
            return REDIRECT_LAUNCHPAD_PLAN_PLANS;
        }
        model.addAttribute("plan", planResultRest.plan);
        model.addAttribute("planYamlAsStr", planResultRest.planYamlAsStr);
        return "launchpad/plan/plan-delete";
    }

    @PostMapping("/plan-delete-commit")
    public String deleteCommit(Long id, final RedirectAttributes redirectAttributes) {
        OperationStatusRest operationStatusRest = planTopLevelService.deletePlanById(id);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", Collections.singletonList("#560.40 plan wasn't found, id: "+id) );
        }
        return REDIRECT_LAUNCHPAD_PLAN_PLANS;
    }

    @GetMapping("/plan-archive/{id}")
    public String archive(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        PlanApiData.PlanResult planResultRest = planTopLevelService.getPlan(id);
        if (planResultRest.status== EnumsApi.PlanValidateStatus.PLAN_NOT_FOUND_ERROR) {
            redirectAttributes.addFlashAttribute("errorMessage", planResultRest.errorMessages);
            return REDIRECT_LAUNCHPAD_PLAN_PLANS;
        }
        model.addAttribute("plan", planResultRest.plan);
        model.addAttribute("planYamlAsStr", planResultRest.planYamlAsStr);
        return "launchpad/plan/plan-archive";
    }

    @PostMapping("/plan-archive-commit")
    public String archiveCommit(Long id, final RedirectAttributes redirectAttributes) {
        OperationStatusRest operationStatusRest = planTopLevelService.archivePlanById(id);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", Collections.singletonList("#560.40 plan wasn't found, id: "+id) );
        }
        return REDIRECT_LAUNCHPAD_PLAN_PLANS;
    }

    // ============= Workbooks =============

    @GetMapping("/workbooks/{id}")
    public String workbooks(Model model, @PathVariable Long id, @PageableDefault(size = 5) Pageable pageable, @ModelAttribute("errorMessage") final String errorMessage) {
        model.addAttribute("result", planTopLevelService.getWorkbooksOrderByCreatedOnDesc(id, pageable));
        return "launchpad/plan/workbooks";
    }

    // for AJAX
    @PostMapping("/workbooks-part/{id}")
    public String workbooksPart(Model model, @PathVariable Long id, @PageableDefault(size = 10) Pageable pageable) {
        model.addAttribute("result", planTopLevelService.getWorkbooksOrderByCreatedOnDesc(id, pageable));
        return "launchpad/plan/workbooks :: table";
    }

    @SuppressWarnings("Duplicates")
    @GetMapping(value = "/workbook-add/{id}")
    public String workbookAdd(@ModelAttribute("result") PlanApiData.PlanResult result, @PathVariable Long id, final RedirectAttributes redirectAttributes) {
        PlanApiData.PlanResult planResultRest = planTopLevelService.getPlan(id);
        if (planResultRest.status== EnumsApi.PlanValidateStatus.PLAN_NOT_FOUND_ERROR) {
            redirectAttributes.addFlashAttribute("errorMessage", planResultRest.errorMessages);
            return REDIRECT_LAUNCHPAD_PLAN_PLANS;
        }
        result.plan = planResultRest.plan;
        return "launchpad/plan/workbook-add";
    }

    @PostMapping("/workbook-add-commit")
    public String workbookAddCommit(@ModelAttribute("result") PlanApiData.PlanResult result, Long planId, String poolCode, String inputResourceParams, final RedirectAttributes redirectAttributes) {
        PlanApiData.WorkbookResult workbookResultRest = planTopLevelService.addWorkbook(planId, poolCode, inputResourceParams);
        result.plan = workbookResultRest.plan;
        if (result.plan == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#560.60 plan wasn't found, planId: " + planId);
            return REDIRECT_LAUNCHPAD_PLAN_PLANS;
        }

        if (workbookResultRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", workbookResultRest.errorMessages);
        }
        return "redirect:/launchpad/plan/workbooks/" + planId;
    }

    @GetMapping("/workbook-delete/{planId}/{workbookId}")
    public String workbookDelete(Model model, @PathVariable Long planId, @PathVariable Long workbookId, final RedirectAttributes redirectAttributes) {
        PlanApiData.WorkbookResult result = planTopLevelService.getWorkbookExtended(workbookId);
        if (result.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.errorMessages);
            return REDIRECT_LAUNCHPAD_PLAN_PLANS;
        }
        model.addAttribute("result", result);
        return "launchpad/plan/workbook-delete";
    }

    @PostMapping("/workbook-delete-commit")
    public String workbookDeleteCommit(Long planId, Long workbookId, final RedirectAttributes redirectAttributes) {
        OperationStatusRest operationStatusRest = planTopLevelService.deleteWorkbookById(workbookId);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
            return REDIRECT_LAUNCHPAD_PLAN_PLANS;
        }
        return "redirect:/launchpad/plan/workbooks/"+ planId;
    }

    @GetMapping("/workbook-target-exec-state/{planId}/{state}/{id}")
    public String workbookTargetExecState(@PathVariable Long planId, @PathVariable String state, @PathVariable Long id, final RedirectAttributes redirectAttributes) {
        OperationStatusRest operationStatusRest = planTopLevelService.changeWorkbookExecState(state, id);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
            return REDIRECT_LAUNCHPAD_PLAN_PLANS;
        }
        return "redirect:/launchpad/plan/workbooks/" + planId;
    }

}

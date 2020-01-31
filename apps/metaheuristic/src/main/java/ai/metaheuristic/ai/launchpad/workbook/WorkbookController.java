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

package ai.metaheuristic.ai.launchpad.workbook;

import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.ai.launchpad.context.LaunchpadContextService;
import ai.metaheuristic.ai.launchpad.plan.PlanController;
import ai.metaheuristic.ai.launchpad.plan.PlanTopLevelService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.plan.PlanApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * @author Serge
 * Date: 7/4/2019
 * Time: 3:53 PM
 */
@Controller
@RequestMapping("/launchpad/plan")
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class WorkbookController {

    private final PlanTopLevelService planTopLevelService;
    private final WorkbookTopLevelService workbookTopLevelService;
    private final LaunchpadContextService launchpadContextService;

    // ============= Workbooks =============

    @GetMapping("/workbooks/{planId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'MANAGER')")
    public String workbooks(Model model, @PathVariable Long planId, @PageableDefault(size = 5) Pageable pageable,
                            @ModelAttribute("errorMessage") final String errorMessage, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        model.addAttribute("result", workbookTopLevelService.getWorkbooksOrderByCreatedOnDesc(planId, pageable, context));
        return "launchpad/plan/workbooks";
    }

    // for AJAX
    @PostMapping("/workbooks-part/{planId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'MANAGER')")
    public String workbooksPart(Model model, @PathVariable Long planId,
                                @PageableDefault(size = 10) Pageable pageable, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        model.addAttribute("result", workbookTopLevelService.getWorkbooksOrderByCreatedOnDesc(planId, pageable, context));
        return "launchpad/plan/workbooks :: table";
    }

    @GetMapping(value = "/workbook-add/{planId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String workbookAdd(@ModelAttribute("result") PlanApiData.PlanResult result,
                              @PathVariable Long planId, final RedirectAttributes redirectAttributes, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        PlanApiData.PlanResult planResultRest = planTopLevelService.getPlan(planId, context);
        if (planResultRest.status== EnumsApi.PlanValidateStatus.PLAN_NOT_FOUND_ERROR) {
            redirectAttributes.addFlashAttribute("errorMessage", planResultRest.errorMessages);
            return PlanController.REDIRECT_LAUNCHPAD_PLAN_PLANS;
        }
        result.plan = planResultRest.plan;
        return "launchpad/plan/workbook-add";
    }

    @PostMapping("/workbook-add-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String workbookAddCommit(Long planId, String poolCode, String inputResourceParams,
                                    final RedirectAttributes redirectAttributes, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        PlanApiData.WorkbookResult workbookResultRest = planTopLevelService.addWorkbook(planId, poolCode, inputResourceParams, context);
        if (workbookResultRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", workbookResultRest.errorMessages);
        }
        return "redirect:/launchpad/plan/workbooks/" + planId;
    }

    @GetMapping("/workbook-delete/{planId}/{workbookId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String workbookDelete(Model model, @PathVariable Long planId, @PathVariable Long workbookId,
                                 final RedirectAttributes redirectAttributes, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        PlanApiData.WorkbookResult result = workbookTopLevelService.getWorkbookExtendedForDeletion(workbookId, context);
        if (result.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.errorMessages);
            return PlanController.REDIRECT_LAUNCHPAD_PLAN_PLANS;
        }
        model.addAttribute("result", result);
        return "launchpad/plan/workbook-delete";
    }

    @PostMapping("/workbook-delete-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String workbookDeleteCommit(Long planId, Long workbookId,
                                       final RedirectAttributes redirectAttributes, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        OperationStatusRest operationStatusRest = planTopLevelService.deleteWorkbookById(workbookId, context);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
            return PlanController.REDIRECT_LAUNCHPAD_PLAN_PLANS;
        }
        return "redirect:/launchpad/plan/workbooks/"+ planId;
    }

    @GetMapping("/workbook-target-exec-state/{planId}/{state}/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String workbookTargetExecState(@PathVariable Long planId, @PathVariable String state, @PathVariable Long id,
                                          final RedirectAttributes redirectAttributes, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        OperationStatusRest operationStatusRest = planTopLevelService.changeWorkbookExecState(state, id, context);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
            return PlanController.REDIRECT_LAUNCHPAD_PLAN_PLANS;
        }
        return "redirect:/launchpad/plan/workbooks/" + planId;
    }


}

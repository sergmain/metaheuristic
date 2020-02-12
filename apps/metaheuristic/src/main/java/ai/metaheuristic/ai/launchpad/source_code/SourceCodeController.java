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

package ai.metaheuristic.ai.launchpad.source_code;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.ai.launchpad.context.LaunchpadContextService;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Collections;

@Controller
@RequestMapping("/launchpad/plan")
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class SourceCodeController {

    public static final String REDIRECT_LAUNCHPAD_PLAN_PLANS = "redirect:/launchpad/sourceCode/plans";

    private final Globals globals;
    private final SourceCodeTopLevelService sourceCodeTopLevelService;
    private final LaunchpadContextService launchpadContextService;

    @GetMapping("/plans")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DATA')")
    public String plans(Model model, @PageableDefault(size = 5) Pageable pageable,
                        @ModelAttribute("infoMessages") final ArrayList<String> infoMessages,
                        @ModelAttribute("errorMessage") final ArrayList<String> errorMessage, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        SourceCodeApiData.PlansResult plansResultRest = sourceCodeTopLevelService.getPlans(pageable, false, context);
        ControllerUtils.addMessagesToModel(model, plansResultRest);
        model.addAttribute("result", plansResultRest);
        return "launchpad/plan/plans";
    }

    // for AJAX
    @PostMapping("/plans-part")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DATA')")
    public String plansPart(Model model, @PageableDefault(size = 10) Pageable pageable, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        SourceCodeApiData.PlansResult plansResultRest = sourceCodeTopLevelService.getPlans(pageable, false, context);
        model.addAttribute("result", plansResultRest);
        return "launchpad/sourceCode/plans :: table";
    }

    @GetMapping(value = "/plan-add")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String add(Model model) {
        model.addAttribute("planYamlAsStr", "");
        return "launchpad/plan/plan-add";
    }

    @GetMapping(value = "/plan-edit/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String edit(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, Authentication authentication) {
        if (globals.assetMode==EnumsApi.LaunchpadAssetMode.replicated) {
            redirectAttributes.addFlashAttribute("errorMessage", "#561.010 Can't edit sourceCode while 'replicated' mode of asset is active");
            return REDIRECT_LAUNCHPAD_PLAN_PLANS;
        }
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        SourceCodeApiData.PlanResult planResultRest = sourceCodeTopLevelService.getPlan(id, context);
        if (planResultRest.status== EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR) {
            redirectAttributes.addFlashAttribute("errorMessage", planResultRest.errorMessages);
            return REDIRECT_LAUNCHPAD_PLAN_PLANS;
        }
        model.addAttribute("plan", planResultRest.sourceCode);
        model.addAttribute("planYamlAsStr", planResultRest.planYamlAsStr);
        return "launchpad/plan/plan-edit";
    }

    @GetMapping(value = "/plan-validate/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DATA')")
    public String validate(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        SourceCodeApiData.PlanResult planResultRest = sourceCodeTopLevelService.validatePlan(id, context);
        if (planResultRest.status== EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR) {
            redirectAttributes.addFlashAttribute("errorMessage", planResultRest.errorMessages);
            return REDIRECT_LAUNCHPAD_PLAN_PLANS;
        }

        model.addAttribute("plan", planResultRest.sourceCode);
        model.addAttribute("planYamlAsStr", planResultRest.planYamlAsStr);
        model.addAttribute("infoMessages", planResultRest.infoMessages);
        model.addAttribute("errorMessage", planResultRest.errorMessages);
        return "launchpad/plan/plan-edit";
    }

    @PostMapping(value = "/plan-upload-from-file")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String uploadPlan(final MultipartFile file, final RedirectAttributes redirectAttributes, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        OperationStatusRest operationStatusRest = sourceCodeTopLevelService.uploadPlan(file, context);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
        }
        return REDIRECT_LAUNCHPAD_PLAN_PLANS;
    }

    @PostMapping("/plan-add-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String addFormCommit(String planYamlAsStr, final RedirectAttributes redirectAttributes, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        SourceCodeApiData.PlanResult planResultRest = sourceCodeTopLevelService.addPlan(planYamlAsStr, context);
        if (planResultRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", planResultRest.errorMessages);
        }
        if (planResultRest.status== EnumsApi.SourceCodeValidateStatus.OK ) {
            redirectAttributes.addFlashAttribute("infoMessages", Collections.singletonList("Validation result: OK"));
        }
        return REDIRECT_LAUNCHPAD_PLAN_PLANS;
    }

    @PostMapping("/plan-edit-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String editFormCommit(Model model, Long planId, String planYamlAsStr, final RedirectAttributes redirectAttributes, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        SourceCodeApiData.PlanResult planResultRest = sourceCodeTopLevelService.updatePlan(planId, planYamlAsStr, context);
        if (planResultRest.isErrorMessages()) {
            model.addAttribute("errorMessage", planResultRest.errorMessages);
            return "redirect:/launchpad/plan/plan-edit/"+planResultRest.sourceCode.getId();
        }

        if (planResultRest.status== EnumsApi.SourceCodeValidateStatus.OK ) {
            redirectAttributes.addFlashAttribute("infoMessages", Collections.singletonList("Validation result: OK"));
        }
        return "redirect:/launchpad/plan/plan-edit/"+planResultRest.sourceCode.getId();
    }

    @GetMapping("/plan-delete/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String delete(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, Authentication authentication) {
        if (globals.assetMode==EnumsApi.LaunchpadAssetMode.replicated) {
            redirectAttributes.addFlashAttribute("errorMessage", "#561.015 Can't delete sourceCode while 'replicated' mode of asset is active");
            return REDIRECT_LAUNCHPAD_PLAN_PLANS;
        }
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        SourceCodeApiData.PlanResult planResultRest = sourceCodeTopLevelService.getPlan(id, context);
        if (planResultRest.status== EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR) {
            redirectAttributes.addFlashAttribute("errorMessage", planResultRest.errorMessages);
            return REDIRECT_LAUNCHPAD_PLAN_PLANS;
        }
        model.addAttribute("plan", planResultRest.sourceCode);
        model.addAttribute("planYamlAsStr", planResultRest.planYamlAsStr);
        return "launchpad/plan/plan-delete";
    }

    @PostMapping("/plan-delete-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String deleteCommit(Long id, final RedirectAttributes redirectAttributes, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        OperationStatusRest operationStatusRest = sourceCodeTopLevelService.deletePlanById(id, context);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", Collections.singletonList("#561.020 sourceCode wasn't found, id: "+id) );
        }
        return REDIRECT_LAUNCHPAD_PLAN_PLANS;
    }

    @GetMapping("/plan-archive/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DATA')")
    public String archive(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        SourceCodeApiData.PlanResult planResultRest = sourceCodeTopLevelService.getPlan(id, context);
        if (planResultRest.status== EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR) {
            redirectAttributes.addFlashAttribute("errorMessage", planResultRest.errorMessages);
            return REDIRECT_LAUNCHPAD_PLAN_PLANS;
        }
        model.addAttribute("plan", planResultRest.sourceCode);
        model.addAttribute("planYamlAsStr", planResultRest.planYamlAsStr);
        return "launchpad/plan/plan-archive";
    }

    @PostMapping("/plan-archive-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String archiveCommit(Long id, final RedirectAttributes redirectAttributes, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        OperationStatusRest operationStatusRest = sourceCodeTopLevelService.archivePlanById(id, context);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", Collections.singletonList("#561.030 sourceCode wasn't found, id: "+id) );
        }
        return REDIRECT_LAUNCHPAD_PLAN_PLANS;
    }

}

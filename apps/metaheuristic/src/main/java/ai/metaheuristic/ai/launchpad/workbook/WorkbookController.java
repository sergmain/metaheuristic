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
import ai.metaheuristic.ai.launchpad.source_code.SourceCodeController;
import ai.metaheuristic.ai.launchpad.source_code.SourceCodeTopLevelService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * @author Serge
 * Date: 7/4/2019
 * Time: 3:53 PM
 */
@Controller
@RequestMapping("/launchpad/source-code")
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class WorkbookController {

    private final SourceCodeTopLevelService sourceCodeTopLevelService;
    private final WorkbookTopLevelService workbookTopLevelService;
    private final LaunchpadContextService launchpadContextService;

    // ============= Workbooks =============

    @GetMapping("/workbooks/{sourceCodeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'MANAGER')")
    public String workbooks(Model model, @PathVariable Long sourceCodeId, @PageableDefault(size = 5) Pageable pageable,
                            @ModelAttribute("errorMessage") final String errorMessage, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        model.addAttribute("result", workbookTopLevelService.getWorkbooksOrderByCreatedOnDesc(sourceCodeId, pageable, context));
        return "launchpad/source-code/workbooks";
    }

    // for AJAX
    @PostMapping("/workbooks-part/{sourceCodeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'MANAGER')")
    public String workbooksPart(Model model, @PathVariable Long sourceCodeId,
                                @PageableDefault(size = 10) Pageable pageable, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        model.addAttribute("result", workbookTopLevelService.getWorkbooksOrderByCreatedOnDesc(sourceCodeId, pageable, context));
        return "launchpad/source-code/workbooks :: table";
    }

    @GetMapping(value = "/workbook-add/{sourceCodeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String workbookAdd(@ModelAttribute("result") SourceCodeApiData.SourceCodeResult result,
                              @PathVariable Long sourceCodeId, final RedirectAttributes redirectAttributes, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        SourceCodeApiData.SourceCodeResult sourceCodeResultRest = sourceCodeTopLevelService.getSourceCode(sourceCodeId, context);
        if (sourceCodeResultRest.status== EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR) {
            redirectAttributes.addFlashAttribute("errorMessage", sourceCodeResultRest.errorMessages);
            return SourceCodeController.REDIRECT_LAUNCHPAD_SOURCE_CODES;
        }
        result.sourceCode = sourceCodeResultRest.sourceCode;
        return "launchpad/source-code/workbook-add";
    }

    @PostMapping("/workbook-add-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String workbookAddCommit(Long planId, String variable, final RedirectAttributes redirectAttributes, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        SourceCodeApiData.WorkbookResult workbookResultRest = sourceCodeTopLevelService.addWorkbook(planId, variable, context);
        if (workbookResultRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", workbookResultRest.errorMessages);
        }
        return "redirect:/launchpad/source-code/workbooks/" + planId;
    }

    @GetMapping("/workbook-delete/{sourceCodeId}/{workbookId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String workbookDelete(Model model, @PathVariable Long planId, @PathVariable Long workbookId,
                                 final RedirectAttributes redirectAttributes, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        SourceCodeApiData.WorkbookResult result = workbookTopLevelService.getWorkbookExtendedForDeletion(workbookId, context);
        if (result.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.errorMessages);
            return SourceCodeController.REDIRECT_LAUNCHPAD_SOURCE_CODES;
        }
        model.addAttribute("result", result);
        return "launchpad/source-code/workbook-delete";
    }

    @PostMapping("/workbook-delete-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String workbookDeleteCommit(Long planId, Long workbookId,
                                       final RedirectAttributes redirectAttributes, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        OperationStatusRest operationStatusRest = sourceCodeTopLevelService.deleteWorkbookById(workbookId, context);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
            return SourceCodeController.REDIRECT_LAUNCHPAD_SOURCE_CODES;
        }
        return "redirect:/launchpad/source-code/workbooks/"+ planId;
    }

    @GetMapping("/workbook-target-exec-state/{sourceCodeId}/{state}/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String workbookTargetExecState(@PathVariable Long sourceCodeId, @PathVariable String state, @PathVariable Long id,
                                          final RedirectAttributes redirectAttributes, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        OperationStatusRest operationStatusRest = sourceCodeTopLevelService.changeWorkbookExecState(state, id, context);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
            return SourceCodeController.REDIRECT_LAUNCHPAD_SOURCE_CODES;
        }
        return "redirect:/launchpad/source-code/workbooks/" + sourceCodeId;
    }


}

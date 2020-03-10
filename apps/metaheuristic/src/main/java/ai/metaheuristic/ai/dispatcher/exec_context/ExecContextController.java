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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeController;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeTopLevelService;
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
@RequestMapping("/dispatcher/source-code")
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class ExecContextController {

    private final SourceCodeTopLevelService sourceCodeTopLevelService;
    private final ExecContextTopLevelService execContextTopLevelService;
    private final ExecContextService execContextService;
    private final UserContextService userContextService;
    private final ExecContextCreatorService execContextCreatorService;

    // ============= Exec contexts =============

    @GetMapping("/exec-contexts/{sourceCodeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'MANAGER')")
    public String execContexts(Model model, @PathVariable Long sourceCodeId, @PageableDefault(size = 5) Pageable pageable,
                            @ModelAttribute("errorMessage") final String errorMessage, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        model.addAttribute("result", execContextTopLevelService.getExecContextsOrderByCreatedOnDesc(sourceCodeId, pageable, context));
        return "dispatcher/source-code/exec-contexts";
    }

    // for AJAX
    @PostMapping("/exec-contexts-part/{sourceCodeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'MANAGER')")
    public String execContextPart(Model model, @PathVariable Long sourceCodeId,
                                @PageableDefault(size = 10) Pageable pageable, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        model.addAttribute("result", execContextTopLevelService.getExecContextsOrderByCreatedOnDesc(sourceCodeId, pageable, context));
        return "dispatcher/source-code/exec-contexts :: table";
    }

    @GetMapping(value = "/exec-context-add/{sourceCodeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String execContextAdd(@ModelAttribute("result") SourceCodeApiData.SourceCodeResult result,
                              @PathVariable Long sourceCodeId, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        SourceCodeApiData.SourceCodeResult sourceCodeResultRest = sourceCodeTopLevelService.getSourceCode(sourceCodeId, context);
        if (sourceCodeResultRest.status== EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR) {
            redirectAttributes.addFlashAttribute("errorMessage", sourceCodeResultRest.getErrorMessagesAsList());
            return SourceCodeController.REDIRECT_DISPATCHER_SOURCE_CODES;
        }
        result.sourceCode = sourceCodeResultRest.sourceCode;
        return "dispatcher/source-code/exec-context-add";
    }

    /**
     * right now reference to global variable isn't supported. all global variables must be specified in SourceCode.
     */
    @PostMapping("/exec-context-code-add-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String execContextAddCommit(Long sourceCodeId, String variable, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        ExecContextCreatorService.ExecContextCreationResult execContextResultRest = execContextCreatorService.createExecContext(sourceCodeId, context);
        if (execContextResultRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", execContextResultRest.getErrorMessagesAsList());
        }
        return "redirect:/dispatcher/source-code/exec-contexts/" + sourceCodeId;
    }

    @GetMapping("/exec-context-delete/{sourceCodeId}/{execContextId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String execContextDelete(Model model, @PathVariable Long sourceCodeId, @PathVariable Long execContextId,
                                 final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        SourceCodeApiData.ExecContextResult result = execContextTopLevelService.getExecContextExtendedForDeletion(execContextId, context);
        if (result.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.getErrorMessagesAsList());
            return SourceCodeController.REDIRECT_DISPATCHER_SOURCE_CODES;
        }
        model.addAttribute("result", result);
        return "dispatcher/source-code/exec-context-delete";
    }

    @PostMapping("/exec-context-delete-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String execContextDeleteCommit(Long sourceCodeId, Long execContextId,
                                       final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        OperationStatusRest operationStatusRest = sourceCodeTopLevelService.deleteExecContextById(execContextId, context);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.getErrorMessagesAsList());
            return SourceCodeController.REDIRECT_DISPATCHER_SOURCE_CODES;
        }
        return "redirect:/dispatcher/source-code/exec-contexts/"+ sourceCodeId;
    }

    @GetMapping("/exec-context-target-state/{sourceCodeId}/{state}/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String execContextTargetState(@PathVariable Long sourceCodeId, @PathVariable String state, @PathVariable Long id,
                                          final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        OperationStatusRest operationStatusRest = sourceCodeTopLevelService.changeExecContextState(state, id, context);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.getErrorMessagesAsList());
            return SourceCodeController.REDIRECT_DISPATCHER_SOURCE_CODES;
        }
        return "redirect:/dispatcher/source-code/exec-contexts/" + sourceCodeId;
    }


}

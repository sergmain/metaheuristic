/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeController;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeService;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;

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

    private final SourceCodeService sourceCodeService;
    private final ExecContextTopLevelService execContextTopLevelService;
    private final ExecContextService execContextService;
    private final UserContextService userContextService;
    private final ExecContextCreatorTopLevelService execContextCreatorTopLevelService;

    @GetMapping("/exec-contexts/{sourceCodeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'MANAGER')")
    public String execContexts(Model model, @PathVariable Long sourceCodeId, @PageableDefault(size = 5) Pageable pageable,
                            @ModelAttribute("errorMessage") final String errorMessage, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        model.addAttribute("result", execContextService.getExecContextsOrderByCreatedOnDesc(sourceCodeId, pageable, context));
        return "dispatcher/source-code/exec-contexts";
    }

    // for AJAX
    @PostMapping("/exec-contexts-part/{sourceCodeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'MANAGER')")
    public String execContextPart(Model model, @PathVariable Long sourceCodeId,
                                @PageableDefault(size = 10) Pageable pageable, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        model.addAttribute("result", execContextService.getExecContextsOrderByCreatedOnDesc(sourceCodeId, pageable, context));
        return "dispatcher/source-code/exec-contexts :: table";
    }

    @GetMapping("/exec-context-state/{sourceCodeId}/{execContextId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'MANAGER', 'OPERATOR')")
    public String execContextsState(Model model, @PathVariable Long sourceCodeId,  @PathVariable Long execContextId,
                                    @ModelAttribute("errorMessage") final String errorMessage, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        ExecContextApiData.ExecContextStateResult execContextState = execContextTopLevelService.getExecContextState(sourceCodeId, execContextId, context, authentication);
        if (execContextState.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", execContextState.getErrorMessagesAsList());
            return "redirect:/dispatcher/source-code/exec-contexts/" + sourceCodeId;
        }
        model.addAttribute("result", execContextState);
        return "dispatcher/source-code/exec-context-state";
    }

    @GetMapping(value = "/exec-context-add/{sourceCodeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String execContextAdd(Model model, @PathVariable Long sourceCodeId, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        SourceCodeApiData.SourceCodeResult sourceCodeResultRest = sourceCodeService.getSourceCode(sourceCodeId, context);
        if (sourceCodeResultRest.validationResult.status== EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR) {
            redirectAttributes.addFlashAttribute("errorMessage", sourceCodeResultRest.getErrorMessagesAsList());
            return SourceCodeController.REDIRECT_DISPATCHER_SOURCE_CODES;
        }
        model.addAttribute("result", sourceCodeResultRest);
        return "dispatcher/source-code/exec-context-add";
    }

    @GetMapping(value= "/exec-context/{execContextId}/download-variable//{variableId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public HttpEntity<AbstractResource> downloadVariable(
            HttpServletRequest request, @PathVariable("execContextId") Long execContextId, @PathVariable("variableId") Long variableId,
            Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);

        final ResponseEntity<AbstractResource> entity;
        try {
            CleanerInfo resource = execContextService.downloadVariable(execContextId, variableId, context.getCompanyId());
            if (resource==null) {
                return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
            }
            entity = resource.entity;
            request.setAttribute(Consts.RESOURCES_TO_CLEAN, resource.toClean);
        } catch (CommonErrorWithDataException e) {
            // TODO 2019-10-13 in case of this exception resources won't be cleaned, need to re-write
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
        }
        return entity;
    }


    /**
     * right now,ExecContext can be created with Global variables only.
     */
    @PostMapping("/exec-context-add-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String execContextAddCommit(Long sourceCodeId, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        ExecContextCreatorService.ExecContextCreationResult execContextResultRest = execContextCreatorTopLevelService.createExecContextAndStart(sourceCodeId, context.getCompanyId(), true);
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
        SourceCodeApiData.ExecContextForDeletion result = execContextService.getExecContextExtendedForDeletion(execContextId, context);
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
        OperationStatusRest operationStatusRest = execContextTopLevelService.deleteExecContextById(execContextId, context);
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
        OperationStatusRest operationStatusRest = execContextTopLevelService.changeExecContextState(state, id, context);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.getErrorMessagesAsList());
            return SourceCodeController.REDIRECT_DISPATCHER_SOURCE_CODES;
        }
        return "redirect:/dispatcher/source-code/exec-contexts/" + sourceCodeId;
    }

    @GetMapping("/exec-context-task-exec-info/{sourceCodeId}/{execContextId}/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'MANAGER', 'OPERATOR')")
    public String taskExecInfo(Model model, @PathVariable Long sourceCodeId, @PathVariable Long execContextId, @PathVariable Long taskId, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        ExecContextApiData.TaskExecInfo taskExecInfo = execContextTopLevelService.getTaskExecInfo(sourceCodeId, execContextId, taskId);
        model.addAttribute("result", taskExecInfo);
        return "dispatcher/source-code/exec-context-task-exec-info";
    }

}

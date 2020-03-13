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

package ai.metaheuristic.ai.dispatcher.source_code;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
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
@RequestMapping("/dispatcher/source-code")
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class SourceCodeController {

    public static final String REDIRECT_DISPATCHER_SOURCE_CODES = "redirect:/dispatcher/source-code/source-codes";

    private final Globals globals;
    private final SourceCodeTopLevelService sourceCodeTopLevelService;
    private final UserContextService userContextService;

    @GetMapping("/source-codes")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DATA')")
    public String sourceCodes(Model model, @PageableDefault(size = 5) Pageable pageable,
                        @ModelAttribute("infoMessages") final ArrayList<String> infoMessages,
                        @ModelAttribute("errorMessage") final ArrayList<String> errorMessage, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        SourceCodeApiData.SourceCodesResult sourceCodesResultRest = sourceCodeTopLevelService.getSourceCodes(pageable, false, context);
        ControllerUtils.addMessagesToModel(model, sourceCodesResultRest);
        model.addAttribute("result", sourceCodesResultRest);
        return "dispatcher/source-code/source-codes";
    }

    // for AJAX
    @PostMapping("/source-codes-part")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DATA')")
    public String sourceCodesPart(Model model, @PageableDefault(size = 10) Pageable pageable, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        SourceCodeApiData.SourceCodesResult sourceCodesResultRest = sourceCodeTopLevelService.getSourceCodes(pageable, false, context);
        model.addAttribute("result", sourceCodesResultRest);
        return "dispatcher/source-code/source-codes :: table";
    }

    @GetMapping(value = "/source-code-add")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String add(Model model) {
        model.addAttribute("sourceCodeYamlAsStr", "");
        return "dispatcher/source-code/source-code-add";
    }

    @GetMapping(value = "/source-code-edit/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String edit(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, Authentication authentication) {
        if (globals.assetMode== EnumsApi.DispatcherAssetMode.replicated) {
            redirectAttributes.addFlashAttribute("errorMessage", "#561.010 Can't edit sourceCode while 'replicated' mode of asset is active");
            return REDIRECT_DISPATCHER_SOURCE_CODES;
        }
        DispatcherContext context = userContextService.getContext(authentication);
        SourceCodeApiData.SourceCodeResult sourceCodeResultRest = sourceCodeTopLevelService.getSourceCode(id, context);
        if (sourceCodeResultRest.status.status== EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR) {
            redirectAttributes.addFlashAttribute("errorMessage", sourceCodeResultRest.getErrorMessagesAsList());
            return REDIRECT_DISPATCHER_SOURCE_CODES;
        }
        model.addAttribute("sourceCode", sourceCodeResultRest.sourceCode);
        model.addAttribute("sourceCodeYamlAsStr", sourceCodeResultRest.sourceCodeYamlAsStr);
        return "dispatcher/source-code/source-code-edit";
    }

    @GetMapping(value = "/source-code-validate/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DATA')")
    public String validate(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        SourceCodeApiData.SourceCodeResult sourceCodeResultRest = sourceCodeTopLevelService.validateSourceCode(id, context);
        if (sourceCodeResultRest.status.status== EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR) {
            redirectAttributes.addFlashAttribute("errorMessage", sourceCodeResultRest.getErrorMessagesAsList());
            return REDIRECT_DISPATCHER_SOURCE_CODES;
        }

        model.addAttribute("sourceCode", sourceCodeResultRest.sourceCode);
        model.addAttribute("sourceCodeYamlAsStr", sourceCodeResultRest.sourceCodeYamlAsStr);
        model.addAttribute("infoMessages", sourceCodeResultRest.infoMessages);
        model.addAttribute("errorMessage", sourceCodeResultRest.getErrorMessagesAsList());
        return "dispatcher/source-code/source-code-edit";
    }

    @PostMapping(value = "/source-code-upload-from-file")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String uploadSourceCode(final MultipartFile file, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        OperationStatusRest operationStatusRest = sourceCodeTopLevelService.uploadSourceCode(file, context);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.getErrorMessagesAsList());
        }
        return REDIRECT_DISPATCHER_SOURCE_CODES;
    }

    @PostMapping("/source-code-add-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String addFormCommit(String sourceCodeYamlAsStr, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        SourceCodeApiData.SourceCodeResult sourceCodeResultRest = sourceCodeTopLevelService.addSourceCode(sourceCodeYamlAsStr, context);
        if (sourceCodeResultRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", sourceCodeResultRest.getErrorMessagesAsList());
        }
        if (sourceCodeResultRest.status.status== EnumsApi.SourceCodeValidateStatus.OK ) {
            redirectAttributes.addFlashAttribute("infoMessages", Collections.singletonList("Validation result: OK"));
        }
        return REDIRECT_DISPATCHER_SOURCE_CODES;
    }

    @PostMapping("/source-code-edit-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String editFormCommit(Model model, Long sourceCodeId, String sourceCodeYamlAsStr, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        SourceCodeApiData.SourceCodeResult sourceCodeResultRest = sourceCodeTopLevelService.updateSourceCode(sourceCodeId, sourceCodeYamlAsStr, context);
        if (sourceCodeResultRest.isErrorMessages()) {
            model.addAttribute("errorMessage", sourceCodeResultRest.getErrorMessagesAsList());
            return "redirect:/dispatcher/source-code/source-code-edit/"+ sourceCodeResultRest.sourceCode.getId();
        }

        if (sourceCodeResultRest.status.status== EnumsApi.SourceCodeValidateStatus.OK ) {
            redirectAttributes.addFlashAttribute("infoMessages", Collections.singletonList("Validation result: OK"));
        }
        return "redirect:/dispatcher/source-code/source-code-edit/"+ sourceCodeResultRest.sourceCode.getId();
    }

    @GetMapping("/source-code-delete/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String delete(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, Authentication authentication) {
        if (globals.assetMode== EnumsApi.DispatcherAssetMode.replicated) {
            redirectAttributes.addFlashAttribute("errorMessage", "#561.015 Can't delete sourceCode while 'replicated' mode of asset is active");
            return REDIRECT_DISPATCHER_SOURCE_CODES;
        }
        DispatcherContext context = userContextService.getContext(authentication);
        SourceCodeApiData.SourceCodeResult sourceCodeResultRest = sourceCodeTopLevelService.getSourceCode(id, context);
        if (sourceCodeResultRest.status.status== EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR) {
            redirectAttributes.addFlashAttribute("errorMessage", sourceCodeResultRest.getErrorMessagesAsList());
            return REDIRECT_DISPATCHER_SOURCE_CODES;
        }
        model.addAttribute("sourceCode", sourceCodeResultRest.sourceCode);
        model.addAttribute("sourceCodeYamlAsStr", sourceCodeResultRest.sourceCodeYamlAsStr);
        return "dispatcher/source-code/source-code-delete";
    }

    @PostMapping("/source-code-delete-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String deleteCommit(Long id, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        OperationStatusRest operationStatusRest = sourceCodeTopLevelService.deleteSourceCodeById(id, context);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", Collections.singletonList("#561.020 sourceCode wasn't found, id: "+id) );
        }
        return REDIRECT_DISPATCHER_SOURCE_CODES;
    }

    @GetMapping("/source-code-archive/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DATA')")
    public String archive(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        SourceCodeApiData.SourceCodeResult sourceCodeResultRest = sourceCodeTopLevelService.getSourceCode(id, context);
        if (sourceCodeResultRest.status.status== EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR) {
            redirectAttributes.addFlashAttribute("errorMessage", sourceCodeResultRest.getErrorMessagesAsList());
            return REDIRECT_DISPATCHER_SOURCE_CODES;
        }
        model.addAttribute("sourceCode", sourceCodeResultRest.sourceCode);
        model.addAttribute("sourceCodeYamlAsStr", sourceCodeResultRest.sourceCodeYamlAsStr);
        return "dispatcher/source-code/source-code-archive";
    }

    @PostMapping("/source-code-archive-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public String archiveCommit(Long id, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        OperationStatusRest operationStatusRest = sourceCodeTopLevelService.archiveSourceCodeById(id, context);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", Collections.singletonList("#561.030 source code wasn't found, id: "+id) );
        }
        return REDIRECT_DISPATCHER_SOURCE_CODES;
    }

}

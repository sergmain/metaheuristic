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

package ai.metaheuristic.ai.dispatcher.source_code;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;

@SuppressWarnings("DuplicatedCode")
@Controller
@RequestMapping("/dispatcher/source-code")
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class SourceCodeController {

    public static final String REDIRECT_DISPATCHER_SOURCE_CODES = "redirect:/dispatcher/source-code/source-codes";

    private final Globals globals;
    private final SourceCodeService sourceCodeService;
    private final SourceCodeTopLevelService sourceCodeTopLevelService;
    private final UserContextService userContextService;

    @GetMapping("/source-codes")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'MANAGER', 'DATA')")
    public String sourceCodes(Model model, @PageableDefault Pageable pageable,
                        @ModelAttribute("infoMessages") final ArrayList<String> infoMessages,
                        @ModelAttribute("errorMessage") final ArrayList<String> errorMessage, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        SourceCodeApiData.SourceCodesResult sourceCodesResultRest = sourceCodeService.getSourceCodes(pageable, false, context);
        ControllerUtils.addMessagesToModel(model, sourceCodesResultRest);
        model.addAttribute("result", sourceCodesResultRest);
        return "dispatcher/source-code/source-codes";
    }

    // for AJAX
    @PostMapping("/source-codes-part")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'MANAGER', 'DATA')")
    public String sourceCodesPart(Model model, @PageableDefault Pageable pageable, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        SourceCodeApiData.SourceCodesResult sourceCodesResultRest = sourceCodeService.getSourceCodes(pageable, false, context);
        model.addAttribute("result", sourceCodesResultRest);
        return "dispatcher/source-code/source-codes :: table";
    }

    @GetMapping(value = "/source-code-add")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public String add(Model model) {
        model.addAttribute("sourceCodeYamlAsStr", "");
        return "dispatcher/source-code/source-code-add";
    }

    @GetMapping(value = "/source-code-view/{id}")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public String edit(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        SourceCodeApiData.SourceCodeResult sourceCodeResultRest = sourceCodeTopLevelService.getSourceCode(id, context);
        if (sourceCodeResultRest.validationResult.status== EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR) {
            ControllerUtils.initRedirectAttributes(redirectAttributes, sourceCodeResultRest);
            return REDIRECT_DISPATCHER_SOURCE_CODES;
        }
        model.addAttribute("result", sourceCodeResultRest);
        return "dispatcher/source-code/source-code-view";
    }

    @GetMapping(value = "/source-code-devs/{id}")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public String development(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        SourceCodeData.Development development = sourceCodeTopLevelService.getSourceCodeDevs(id, context);
        if (development.isErrorMessages()) {
            ControllerUtils.initRedirectAttributes(redirectAttributes, development);
            return REDIRECT_DISPATCHER_SOURCE_CODES;
        }
        model.addAttribute("result", development);
        return "dispatcher/source-code/source-code-devs";
    }

    @GetMapping(value= "/source-code-dev-generate/{sourceCodeId}/{processCode}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public HttpEntity<AbstractResource> downloadVariable(
            HttpServletRequest request, @PathVariable("sourceCodeId") Long sourceCodeId, @PathVariable("processCode") String processCode,
            Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);

        final ResponseEntity<AbstractResource> entity;
        try {
            CleanerInfo resource = sourceCodeTopLevelService.generateDirsForDev(sourceCodeId, processCode, context.getCompanyId());
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

    @GetMapping(value = "/source-code-validate/{id}")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'MANAGER', 'DATA')")
    public String validate(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        SourceCodeApiData.SourceCodeResult sourceCodeResultRest = sourceCodeService.validateSourceCode(id, context);
        if (sourceCodeResultRest.validationResult.status== EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR) {
            ControllerUtils.initRedirectAttributes(redirectAttributes, sourceCodeResultRest);
            return REDIRECT_DISPATCHER_SOURCE_CODES;
        }

        model.addAttribute("result", sourceCodeResultRest);
        model.addAttribute("infoMessages", sourceCodeResultRest.infoMessages);
        model.addAttribute("errorMessage", sourceCodeResultRest.getErrorMessagesAsList());
        return "dispatcher/source-code/source-code-view";
    }

    @PostMapping(value = "/source-code-upload-from-file")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public String uploadSourceCode(final MultipartFile file, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        OperationStatusRest operationStatusRest = sourceCodeTopLevelService.uploadSourceCode(file, context);
        ControllerUtils.initRedirectAttributes(redirectAttributes, operationStatusRest);
        return REDIRECT_DISPATCHER_SOURCE_CODES;
    }

    @PostMapping("/source-code-add-commit")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public String addFormCommit(String sourceCodeYamlAsStr, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        SourceCodeApiData.SourceCodeResult sourceCodeResultRest = sourceCodeTopLevelService.createSourceCode(sourceCodeYamlAsStr, context.getCompanyId());
        ControllerUtils.initRedirectAttributes(redirectAttributes, sourceCodeResultRest);
        return REDIRECT_DISPATCHER_SOURCE_CODES;
    }

    @PostMapping("/source-code-edit-commit")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public String editFormCommit(Long sourceCodeId, String sourceCodeYamlAsStr) {
        throw new IllegalStateException("Not supported any more");
    }

    @GetMapping("/source-code-delete/{id}")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public String delete(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, Authentication authentication) {
        if (globals.dispatcher.asset.mode== EnumsApi.DispatcherAssetMode.replicated) {
            redirectAttributes.addFlashAttribute("errorMessage", "#561.015 Can't delete sourceCode while 'replicated' mode of asset is active");
            return REDIRECT_DISPATCHER_SOURCE_CODES;
        }
        DispatcherContext context = userContextService.getContext(authentication);
        SourceCodeApiData.SourceCodeResult sourceCodeResultRest = sourceCodeTopLevelService.getSourceCode(id, context);
        if (sourceCodeResultRest.validationResult.status== EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR) {
            ControllerUtils.initRedirectAttributes(redirectAttributes, sourceCodeResultRest);
            return REDIRECT_DISPATCHER_SOURCE_CODES;
        }
        model.addAttribute("result", sourceCodeResultRest);
        return "dispatcher/source-code/source-code-delete";
    }

    @PostMapping("/source-code-delete-commit")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public String deleteCommit(Long id, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        OperationStatusRest operationStatusRest = sourceCodeService.deleteSourceCodeById(id, context);
        ControllerUtils.initRedirectAttributes(redirectAttributes, operationStatusRest);
        return REDIRECT_DISPATCHER_SOURCE_CODES;
    }

    @GetMapping("/source-code-archive/{id}")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'MANAGER', 'DATA')")
    public String archive(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        SourceCodeApiData.SourceCodeResult sourceCodeResultRest = sourceCodeTopLevelService.getSourceCode(id, context);
        if (sourceCodeResultRest.validationResult.status== EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR) {
            ControllerUtils.initRedirectAttributes(redirectAttributes, sourceCodeResultRest);
            return REDIRECT_DISPATCHER_SOURCE_CODES;
        }
        model.addAttribute("result", sourceCodeResultRest);
        return "dispatcher/source-code/source-code-archive";
    }

    @PostMapping("/source-code-archive-commit")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public String archiveCommit(Long id, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        OperationStatusRest operationStatusRest = sourceCodeService.archiveSourceCodeById(id, context);
        ControllerUtils.initRedirectAttributes(redirectAttributes, operationStatusRest);
        return REDIRECT_DISPATCHER_SOURCE_CODES;
    }

}

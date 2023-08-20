/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.rest.v1;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeTopLevelService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeTxService;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@RestController
@RequestMapping("/rest/v1/dispatcher/source-code")
@Profile("dispatcher")
@CrossOrigin
//@CrossOrigin(origins="*", maxAge=3600)
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class SourceCodeRestController {

    private final SourceCodeTxService sourceCodeTxService;
    private final SourceCodeTopLevelService sourceCodeTopLevelService;
    private final UserContextService userContextService;

    @GetMapping("/source-codes")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'MANAGER', 'DATA')")
    public SourceCodeApiData.SourceCodesResult sourceCodes(@PageableDefault(size = 5) Pageable pageable, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return sourceCodeTxService.getSourceCodes(pageable, false, context);
    }

    @GetMapping("/source-codes-archived-only")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'MANAGER', 'DATA')")
    public SourceCodeApiData.SourceCodesResult sourceCodeArchivedOnly(@PageableDefault(size = 5) Pageable pageable, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return sourceCodeTxService.getSourceCodes(pageable, true, context);
    }

    @GetMapping(value = "/source-code/{id}")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'MANAGER', 'DATA')")
    public SourceCodeApiData.SourceCodeResult edit(@PathVariable Long id, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return sourceCodeTopLevelService.getSourceCode(id, context);
    }

    @GetMapping(value = "/source-code-validate/{id}")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'MANAGER', 'DATA')")
    public SourceCodeApiData.SourceCodeResult validate(@PathVariable Long id, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return sourceCodeTxService.validateSourceCode(id, context);
    }

    @PostMapping("/source-code-add-commit")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public SourceCodeApiData.SourceCodeResult addFormCommit(@RequestParam(name = "source") String sourceCodeYamlAsStr, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return sourceCodeTopLevelService.createSourceCode(sourceCodeYamlAsStr, context.getCompanyId());
    }

    @PostMapping("/source-code-edit-commit")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public SourceCodeApiData.SourceCodeResult editFormCommit(Long sourceCodeId, @RequestParam(name = "source") String sourceCodeYamlAsStr) {
        throw new IllegalStateException("Not supported any more");
    }

    @PostMapping("/source-code-delete-commit")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest deleteCommit(Long id, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return sourceCodeTxService.deleteSourceCodeById(id, context);
    }

    @PostMapping("/source-code-archive-commit")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest archiveCommit(Long id, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return sourceCodeTxService.archiveSourceCodeById(id, context);
    }

    @PostMapping(value = "/source-code-upload-from-file")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest uploadSourceCode(final MultipartFile file, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return sourceCodeTopLevelService.uploadSourceCode(file, context);
    }

    // TODO p0 2023-05-20 создание архива с заготовками для задачи. т.е. кнопка Dev
    @GetMapping(value = "/source-code-devs/{id}")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public String development(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        SourceCodeData.Development development = sourceCodeTopLevelService.getSourceCodeDevs(id, context);
        if (development.isErrorMessages()) {
            ControllerUtils.initRedirectAttributes(redirectAttributes, development);
            return "";
        }
        model.addAttribute("result", development);
        return "dispatcher/source-code/source-code-devs";
    }


    // TODO p0 2023-05-20 создание архива с заготовками для задачи. т.е. кнопка Dev
    @GetMapping(value= "/source-code-dev-generate/{sourceCodeId}/{processCode}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public HttpEntity<AbstractResource> sourceCodeDevGenerate(
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
}

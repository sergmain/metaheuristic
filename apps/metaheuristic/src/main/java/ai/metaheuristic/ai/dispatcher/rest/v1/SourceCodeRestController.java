/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeTxService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.commons.account.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/rest/v1/dispatcher/source-code")
@Profile("dispatcher")
@CrossOrigin
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class SourceCodeRestController {

    private final SourceCodeTxService sourceCodeTxService;
    private final SourceCodeService sourceCodeService;
    private final UserContextService userContextService;

    @GetMapping("/source-codes")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'MANAGER', 'DATA')")
    public SourceCodeApiData.SourceCodesResult sourceCodes(@PageableDefault(size = 5) Pageable pageable, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        return sourceCodeTxService.getSourceCodes(pageable, false, context);
    }

    @GetMapping("/simple-source-codes")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'MANAGER', 'DATA')")
    public SourceCodeApiData.SimpleSourceCodesResult simpleSourceCodes(@PageableDefault(size = 5) Pageable pageable, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        return sourceCodeTxService.getSimpleSourceCodes(false, context);
    }

    @GetMapping("/source-codes-archived-only")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'MANAGER', 'DATA')")
    public SourceCodeApiData.SourceCodesResult sourceCodeArchivedOnly(@PageableDefault(size = 5) Pageable pageable, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        return sourceCodeTxService.getSourceCodes(pageable, true, context);
    }

    @GetMapping(value = "/source-code/{id}")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'MANAGER', 'DATA')")
    public SourceCodeApiData.SourceCodeResult edit(@PathVariable Long id, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        return sourceCodeService.getSourceCode(id, context);
    }

    @GetMapping(value = "/source-code-validate/{id}")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'MANAGER', 'DATA')")
    public SourceCodeApiData.SourceCodeResult validate(@PathVariable Long id, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        return sourceCodeTxService.validateSourceCode(id, context);
    }

    @PostMapping("/source-code-add-commit")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public SourceCodeApiData.SourceCodeResult addFormCommit(@RequestParam(name = "source") String sourceCodeAsStr, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        EnumsApi.SourceCodeLang lang = SourceCodeUtils.determineLang(sourceCodeAsStr);
        return sourceCodeService.createSourceCode(sourceCodeAsStr, lang, context.getCompanyId());
    }

    @PostMapping("/source-code-delete-commit")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest deleteCommit(Long id, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        return sourceCodeTxService.deleteSourceCodeById(id, context);
    }

    @PostMapping("/source-code-archive-commit")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest archiveCommit(Long id, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        return sourceCodeTxService.archiveSourceCodeById(id, context);
    }

    @PostMapping(value = "/source-code-upload-from-file")
    @PreAuthorize("hasAnyRole('MAIN_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest uploadSourceCode(final MultipartFile file, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        return sourceCodeService.uploadSourceCode(file, context);
    }
}

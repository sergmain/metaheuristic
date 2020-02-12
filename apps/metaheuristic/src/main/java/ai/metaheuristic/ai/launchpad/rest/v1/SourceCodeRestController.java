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

package ai.metaheuristic.ai.launchpad.rest.v1;

import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.ai.launchpad.context.LaunchpadContextService;
import ai.metaheuristic.ai.launchpad.source_code.SourceCodeTopLevelService;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/rest/v1/launchpad/source-code")
@Profile("launchpad")
@CrossOrigin
//@CrossOrigin(origins="*", maxAge=3600)
@RequiredArgsConstructor
public class SourceCodeRestController {

    private final SourceCodeTopLevelService sourceCodeTopLevelService;
    private final LaunchpadContextService launchpadContextService;

    // ============= SourceCode =============

    @GetMapping("/source-codes")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DATA')")
    public SourceCodeApiData.SourceCodesResult sourceCodes(@PageableDefault(size = 5) Pageable pageable, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        return sourceCodeTopLevelService.getSourceCodes(pageable, false, context);
    }

    @GetMapping("/source-codes-archived-only")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DATA')")
    public SourceCodeApiData.SourceCodesResult sourceCodeArchivedOnly(@PageableDefault(size = 5) Pageable pageable, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        return sourceCodeTopLevelService.getSourceCodes(pageable, true, context);
    }

    @GetMapping(value = "/source-code/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DATA')")
    public SourceCodeApiData.SourceCodeResult edit(@PathVariable Long id, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        return sourceCodeTopLevelService.getSourceCode(id, context);
    }

    @GetMapping(value = "/source-code-validate/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DATA')")
    public SourceCodeApiData.SourceCodeResult validate(@PathVariable Long id, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        return sourceCodeTopLevelService.validateSourceCode(id, context);
    }

    @PostMapping("/source-code-add-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public SourceCodeApiData.SourceCodeResult addFormCommit(@RequestParam(name = "sourceCodeYaml") String sourceCodeYamlAsStr, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        return sourceCodeTopLevelService.addSourceCode(sourceCodeYamlAsStr, context);
    }

    @PostMapping("/source-code-edit-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public SourceCodeApiData.SourceCodeResult editFormCommit(Long sourceCodeId, @RequestParam(name = "sourceCodeYaml") String sourceCodeYamlAsStr, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        return sourceCodeTopLevelService.updateSourceCode(sourceCodeId, sourceCodeYamlAsStr, context);
    }

    @PostMapping("/source-code-delete-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public OperationStatusRest deleteCommit(Long id, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        return sourceCodeTopLevelService.deleteSourceCodeById(id, context);
    }

    @PostMapping("/source-code-archive-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public OperationStatusRest archiveCommit(Long id, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        return sourceCodeTopLevelService.archiveSourceCodeById(id, context);
    }

    @PostMapping(value = "/source-code-upload-from-file")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public OperationStatusRest uploadSourceCode(final MultipartFile file, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        return sourceCodeTopLevelService.uploadSourceCode(file, context);
    }

}

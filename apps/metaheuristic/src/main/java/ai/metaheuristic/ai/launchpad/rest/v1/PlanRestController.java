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
import ai.metaheuristic.api.data.plan.PlanApiData;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/rest/v1/launchpad/plan")
@Profile("launchpad")
@CrossOrigin
//@CrossOrigin(origins="*", maxAge=3600)
@RequiredArgsConstructor
public class PlanRestController {

    private final SourceCodeTopLevelService sourceCodeTopLevelService;
    private final LaunchpadContextService launchpadContextService;

    // ============= SourceCode =============

    @GetMapping("/plans")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DATA')")
    public PlanApiData.PlansResult plans(@PageableDefault(size = 5) Pageable pageable, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        return sourceCodeTopLevelService.getPlans(pageable, false, context);
    }

    @GetMapping("/plans-archived-only")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DATA')")
    public PlanApiData.PlansResult plansArchivedOnly(@PageableDefault(size = 5) Pageable pageable, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        return sourceCodeTopLevelService.getPlans(pageable, true, context);
    }

    @GetMapping(value = "/plan/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DATA')")
    public PlanApiData.PlanResult edit(@PathVariable Long id, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        return sourceCodeTopLevelService.getPlan(id, context);
    }

    @GetMapping(value = "/plan-validate/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DATA')")
    public PlanApiData.PlanResult validate(@PathVariable Long id, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        return sourceCodeTopLevelService.validatePlan(id, context);
    }

    @PostMapping("/plan-add-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public PlanApiData.PlanResult addFormCommit(@RequestParam(name = "planYaml") String planYamlAsStr, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        return sourceCodeTopLevelService.addPlan(planYamlAsStr, context);
    }

    @PostMapping("/plan-edit-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public PlanApiData.PlanResult editFormCommit(Long planId, @RequestParam(name = "planYaml") String planYamlAsStr, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        return sourceCodeTopLevelService.updatePlan(planId, planYamlAsStr, context);
    }

    @PostMapping("/plan-delete-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public OperationStatusRest deleteCommit(Long id, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        return sourceCodeTopLevelService.deletePlanById(id, context);
    }

    @PostMapping("/plan-archive-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public OperationStatusRest archiveCommit(Long id, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        return sourceCodeTopLevelService.archivePlanById(id, context);
    }

    @PostMapping(value = "/plan-upload-from-file")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public OperationStatusRest uploadPlan(final MultipartFile file, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        return sourceCodeTopLevelService.uploadPlan(file, context);
    }

}

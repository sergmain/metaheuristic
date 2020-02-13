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
import ai.metaheuristic.ai.launchpad.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * @author Serge
 * Date: 7/4/2019
 * Time: 3:55 PM
 */

// all urls in "/rest/v1/launchpad/sourceCode" because of angular.
// need change angular code too but not know
@RequestMapping("/rest/v1/launchpad/source-code")
@RestController
@Profile("launchpad")
@CrossOrigin
@RequiredArgsConstructor
public class ExecContextRestController {

    private final SourceCodeTopLevelService sourceCodeTopLevelService;
    private final ExecContextTopLevelService execContextTopLevelService;
    private final LaunchpadContextService launchpadContextService;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleWorkbookAddingResult {
        public Long workbookId;
    }

    @GetMapping("/workbooks/{sourceCodeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'MANAGER')")
    public SourceCodeApiData.ExecContextsResult workbooks(@PathVariable Long sourceCodeId,
                                                          @PageableDefault(size = 5) Pageable pageable, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        return execContextTopLevelService.getExecContextsOrderByCreatedOnDesc(sourceCodeId, pageable, context);
    }

    /**
     * create ExecContext by uid
     * useful for creating ExecContext from command-line with cURL
     *
     * @return
     */
    @PostMapping("/source-code-exec-context-add-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public SimpleWorkbookAddingResult workbookAddCommit(String sourceCode, String variable, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        SourceCodeApiData.ExecContextResult execContextResult = sourceCodeTopLevelService.addExecContext(sourceCode, variable, context);
        return new SimpleWorkbookAddingResult(execContextResult.execContext.getId());
    }

    /**
     * Only one parameter has to be used - either poolCode or inputResourceParams
     */
    @PostMapping("/exec-context-add-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public SourceCodeApiData.ExecContextResult workbookAddCommit(Long sourceCodeId, String variable, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        //noinspection UnnecessaryLocalVariable
        SourceCodeApiData.ExecContextResult execContextResult = sourceCodeTopLevelService.addExecContext(sourceCodeId, variable, context);
        return execContextResult;
    }

    @GetMapping(value = "/workbook/{sourceCodeId}/{execContextId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public SourceCodeApiData.ExecContextResult workbookEdit(@SuppressWarnings("unused") @PathVariable Long sourceCodeId, @PathVariable Long execContextId) {
        return execContextTopLevelService.getExecContextExtended(execContextId);
    }

    @SuppressWarnings("unused")
    @PostMapping("/workbook-delete-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public OperationStatusRest workbookDeleteCommit(Long sourceCodeId, Long execContextId, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        return sourceCodeTopLevelService.deleteExecContextById(execContextId, context);
    }

    @GetMapping("/workbook-target-exec-state/{sourceCodeId}/{state}/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public OperationStatusRest execContextTargetExecState(
            @SuppressWarnings("unused") @PathVariable Long sourceCodeId, @PathVariable String state,
            @PathVariable Long id, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        return sourceCodeTopLevelService.changeExecContextState(state, id, context);
    }

}

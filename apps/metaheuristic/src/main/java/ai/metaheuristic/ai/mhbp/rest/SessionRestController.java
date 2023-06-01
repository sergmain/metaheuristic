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

package ai.metaheuristic.ai.mhbp.rest;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.mhbp.data.ErrorData;
import ai.metaheuristic.ai.mhbp.data.SessionData;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.ai.mhbp.session.SessionService;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.mhbp.session.SessionTxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * @author Sergio Lissner
 * Date: 3/26/2023
 * Time: 2:57 AM
 */
@RestController
@RequestMapping("/rest/v1/dispatcher/session")
@Slf4j
@RequiredArgsConstructor
@Profile("dispatcher")
public class SessionRestController {

    private final SessionService sessionService;
    private final SessionTxService sessionTxService;
    private final UserContextService userContextService;

    @GetMapping("/sessions")
    public SessionData.SessionStatuses sessions(Pageable pageable) {
        final SessionData.SessionStatuses statuses = sessionService.getStatuses(pageable);
        return statuses;
    }

/*
    @PostMapping("/evaluation-add-commit")
//    @PreAuthorize("hasAnyRole('MASTER_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public SourceCodeApiData.SourceCodeResult addFormCommit(@RequestParam(name = "source") String sourceCodeYamlAsStr, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return sourceCodeTopLevelService.createSourceCode(sourceCodeYamlAsStr, context.getCompanyId());
    }

    @PostMapping("/evaluation-edit-commit")
//    @PreAuthorize("hasAnyRole('MASTER_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public SourceCodeApiData.SourceCodeResult editFormCommit(Long sourceCodeId, @RequestParam(name = "source") String sourceCodeYamlAsStr) {
        throw new IllegalStateException("Not supported any more");
    }
*/

    @PostMapping("/session-delete-commit")
//    @PreAuthorize("hasAnyRole('MASTER_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest deleteCommit(@Nullable Long sessionId, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return sessionTxService.deleteSessionById(sessionId, context);
    }

    @GetMapping("/session-errors/{sessionId}")
    public ErrorData.ErrorsResult errors(Pageable pageable, @PathVariable Long sessionId, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        final ErrorData.ErrorsResult errors = sessionService.getErrors(pageable, sessionId, context);
        return errors;
    }

}

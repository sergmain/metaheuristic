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

import ai.metaheuristic.ai.mhbp.auth.AuthService;
import ai.metaheuristic.ai.mhbp.data.AuthData;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * @author Sergio Lissner
 * Date: 4/13/2023
 * Time: 12:15 AM
 */
@RestController
@RequestMapping("/rest/v1/dispatcher/auth")
@Slf4j
@RequiredArgsConstructor
@Profile("dispatcher")
public class AuthRestController {

    private final AuthService authService;
    private final UserContextService userContextService;

    @GetMapping("/auths")
    public AuthData.Auths auths(Pageable pageable, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        final AuthData.Auths auths = authService.getAuths(pageable, context);
        return auths;
    }

    @GetMapping("/auth/{authId}")
    public AuthData.Auth apis(@PathVariable @Nullable Long authId, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        final AuthData.Auth api = authService.getAuth(authId, context);
        return api;
    }

    @PostMapping("/auth-add-commit")
//    @PreAuthorize("hasAnyRole('MASTER_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest addFormCommit(
            @RequestParam(name = "code") String code,
            @RequestParam(name = "params") String params,
            Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);

        return authService.createAuth(code, params, context);
    }

    @PostMapping("/auth-delete-commit")
//    @PreAuthorize("hasAnyRole('MASTER_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest deleteCommit(Long id, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return authService.deleteAuthById(id, context);
    }

}

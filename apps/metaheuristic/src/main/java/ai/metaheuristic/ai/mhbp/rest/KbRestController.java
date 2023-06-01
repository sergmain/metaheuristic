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
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.mhbp.data.KbData;
import ai.metaheuristic.ai.mhbp.kb.KbService;
import ai.metaheuristic.ai.mhbp.kb.KbTxService;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * @author Sergio Lissner
 * Date: 4/15/2023
 * Time: 3:31 PM
 */
@RestController
@RequestMapping("/rest/v1/dispatcher/kb")
@Slf4j
@RequiredArgsConstructor
@Profile("dispatcher")
public class KbRestController {

    private final KbService kbService;
    private final KbTxService kbTxService;
    private final UserContextService userContextService;

    @GetMapping("/kbs")
    public KbData.Kbs auths(Pageable pageable, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        final KbData.Kbs kbs = kbService.getKbs(pageable, context);
        return kbs;
    }

    @GetMapping("/kb/{kbId}")
    public KbData.Kb apis(@PathVariable @Nullable Long kbId, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        final KbData.Kb api = kbService.getKb(kbId, context);
        return api;
    }

    @PostMapping("/kb-add-commit")
//    @PreAuthorize("hasAnyRole('MASTER_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest addFormCommit(
            @RequestParam(name = "code") String code,
            @RequestParam(name = "params") String params,
            Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);

        return kbTxService.createKb(code, params, context);
    }

    @PostMapping("/kb-delete-commit")
//    @PreAuthorize("hasAnyRole('MASTER_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest deleteCommit(Long id, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return kbTxService.deleteKbById(id, context);
    }

    @PostMapping("/kb-init")
//    @PreAuthorize("hasAnyRole('MASTER_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest initKb(Long id, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return kbService.initKb(id, context);
    }

}

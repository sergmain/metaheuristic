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

package ai.metaheuristic.ai.mh.dispatcher..rest.v1;

import ai.metaheuristic.ai.mh.dispatcher..DispatcherContext;
import ai.metaheuristic.ai.mh.dispatcher..account.AccountTopLevelService;
import ai.metaheuristic.ai.mh.dispatcher..beans.Account;
import ai.metaheuristic.ai.mh.dispatcher..context.UserContextService;
import ai.metaheuristic.ai.mh.dispatcher..data.AccountData;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@SuppressWarnings("Duplicates")
@RestController
@RequestMapping("/rest/v1/mh.dispatcher./account")
@Slf4j
@Profile("mh.dispatcher.")
@CrossOrigin
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN')")
public class AccountRestController {

    private final AccountTopLevelService accountTopLevelService;
    private final UserContextService userContextService;

    @GetMapping("/accounts")
    public AccountData.AccountsResult accounts(@PageableDefault(size = 5) Pageable pageable, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return accountTopLevelService.getAccounts(pageable, context);
    }

    @PostMapping("/account-add-commit")
    public OperationStatusRest addFormCommit(@RequestBody Account account, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return accountTopLevelService.addAccount(account, context);
    }

    @GetMapping(value = "/account/{id}")
    public AccountData.AccountResult getAccount(@PathVariable Long id, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return accountTopLevelService.getAccount(id, context);
    }

    @PostMapping("/account-edit-commit")
    public OperationStatusRest editFormCommit(Long id, String publicName, boolean enabled, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return accountTopLevelService.editFormCommit(id, publicName, enabled, context);
    }

    @PostMapping("/account-role-commit")
    public OperationStatusRest roleFormCommit(Long accountId, String roles, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return accountTopLevelService.roleFormCommit(accountId, roles, context);
    }

    @PostMapping("/account-password-edit-commit")
    public OperationStatusRest passwordEditFormCommit(Long id, String password, String password2, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return accountTopLevelService.passwordEditFormCommit(id, password, password2, context);
    }

}

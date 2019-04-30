/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.rest.v1;

import aiai.ai.launchpad.account.AccountTopLevelService;
import aiai.ai.launchpad.beans.Account;
import aiai.ai.launchpad.data.AccountData;
import aiai.ai.launchpad.data.OperationStatusRest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@SuppressWarnings("Duplicates")
@RestController
@RequestMapping("/rest/v1/launchpad/account")
@Slf4j
@Profile("launchpad")
@CrossOrigin
public class AccountRestController {

    private final AccountTopLevelService accountTopLevelService;

    public AccountRestController(AccountTopLevelService accountTopLevelService) {
        this.accountTopLevelService = accountTopLevelService;
    }

    @GetMapping("/accounts")
    public AccountData.AccountsResult accounts(@PageableDefault(size = 5) Pageable pageable) {
        return accountTopLevelService.getAccounts(pageable);
    }

    @PostMapping("/account-add-commit")
    public OperationStatusRest addFormCommit(@RequestBody Account account) {
        return accountTopLevelService.addAccount(account);
    }

    @GetMapping(value = "/account/{id}")
    public AccountData.AccountResult getAccount(@PathVariable Long id) {
        return accountTopLevelService.getAccount(id);
    }

    @PostMapping("/account-edit-commit")
    public OperationStatusRest editFormCommit(Long id, String publicName, boolean enabled) {
        return accountTopLevelService.editFormCommit(id, publicName, enabled);
    }

    @PostMapping("/account-password-edit-commit")
    public OperationStatusRest passwordEditFormCommit(Long id, String password, String password2) {
        return accountTopLevelService.passwordEditFormCommit(id, password, password2);
    }

}

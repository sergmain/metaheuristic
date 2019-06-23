/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

import ai.metaheuristic.ai.launchpad.account.AccountTopLevelService;
import ai.metaheuristic.ai.launchpad.beans.Account;
import ai.metaheuristic.ai.launchpad.data.AccountData;
import ai.metaheuristic.api.data.OperationStatusRest;
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

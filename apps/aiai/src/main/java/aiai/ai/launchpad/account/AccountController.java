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

package aiai.ai.launchpad.account;

import aiai.ai.Globals;
import aiai.ai.launchpad.beans.Account;
import aiai.ai.launchpad.repositories.AccountRepository;
import aiai.ai.utils.ControllerUtils;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/launchpad/account")
@Profile("launchpad")
public class AccountController {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Globals globals;

    @Data
    public static class AccountsResult {
        public Page<Account> accounts;
    }

    public AccountController(AccountRepository accountRepository, PasswordEncoder passwordEncoder, Globals globals) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.globals = globals;
    }

    @GetMapping("/accounts")
    public String init(@ModelAttribute("result") AccountsResult result, @PageableDefault(size = 5) Pageable pageable, @ModelAttribute("errorMessage") final String errorMessage)  {
        pageable = ControllerUtils.fixPageSize(globals.accountRowsLimit, pageable);
        result.accounts = accountRepository.findAll(pageable);
        return "launchpad/account/accounts";
    }

    // for AJAX
    @PostMapping("/accounts-part")
    public String getAccountsViaAJAX(@ModelAttribute("result") AccountsResult result, @PageableDefault(size=5) Pageable pageable )  {
        pageable = ControllerUtils.fixPageSize(globals.accountRowsLimit, pageable);
        result.accounts = accountRepository.findAll(pageable);
        return "launchpad/account/accounts :: table";
    }

    @GetMapping(value = "/account-add")
    public String add(Model model) {
        model.addAttribute("account", new Account());
        return "launchpad/account/account-add-form";
    }

    @PostMapping("/account-add-form-commit")
    public String addFormCommit(Model model, Account account) {
        if (StringUtils.isBlank(account.getUsername()) || StringUtils.isBlank(account.getPassword()) || StringUtils.isBlank(account.getPassword2()) || StringUtils.isBlank(account.getPublicName())) {
            model.addAttribute("account", account);
            model.addAttribute("errorMessage", "#237.01 Username, password, and public name must be not null");
            return "launchpad/account/account-add-form";
        }

        if (account.getUsername().indexOf('=')!=-1 ) {
            model.addAttribute("account", account);
            model.addAttribute("errorMessage", "#237.04 Username can't contain '='");
            return "launchpad/account/account-add-form";
        }

        if (!account.getPassword().equals(account.getPassword2())) {
            model.addAttribute("account", account);
            model.addAttribute("errorMessage", "#237.07 Both passwords must be equal");
            return "launchpad/account/account-add-form";
        }

        if (accountRepository.findByUsername(account.getUsername())!=null) {
            model.addAttribute("account", account);
            model.addAttribute("errorMessage", String.format("#237.09 Username '%s' was already used", account.getUsername()));
            return "launchpad/account/account-add-form";
        }

        account.setPassword(passwordEncoder.encode(account.getPassword()));
        account.setToken(UUID.randomUUID().toString());
        account.setCreatedOn(System.currentTimeMillis());
        account.setRoles("ROLE_USER");
        account.setAccountNonExpired(true);
        account.setAccountNonLocked(true);
        account.setCredentialsNonExpired(true);
        account.setEnabled(true);

        accountRepository.save(account);
        return "redirect:/launchpad/account/accounts";
    }

    @GetMapping(value = "/account-edit/{id}")
    public String edit(@PathVariable Long id, Model model){
        Optional<Account> optionalAcc = accountRepository.findById(id);
        if (!optionalAcc.isPresent()) {
            return "redirect:/launchpad/account/accounts";
        }
        Account account = optionalAcc.get();
        account.setPassword(null);
        model.addAttribute("account", account);
        return "launchpad/account/account-edit-form";
    }

    @PostMapping("/account-edit-form-commit")
    public String editFormCommit(Account account) {
        Optional<Account> optionalAcc = accountRepository.findById(account.getId());
        if (!optionalAcc.isPresent()) {
            return "redirect:/launchpad/account/accounts";
        }
        Account a = optionalAcc.get();
        a.setEnabled(account.isEnabled());
        a.setPublicName(account.getPublicName());
        accountRepository.save(a);
        return "redirect:/launchpad/account/accounts";
    }

    @GetMapping(value = "/account-password-edit/{id}")
    public String passwordEdit(@PathVariable Long id, Model model){
        Optional<Account> optionalAcc = accountRepository.findById(id);
        if (!optionalAcc.isPresent()) {
            return "redirect:/launchpad/account/accounts";
        }
        Account account = optionalAcc.get();
        account.setPassword(null);
        model.addAttribute("account", account);
        return "launchpad/account/account-password-edit-form";
    }

    @PostMapping("/account-password-edit-form-commit")
    public String passwordEditFormCommit(Model model, Account account) {
        Optional<Account> optionalAcc = accountRepository.findById(account.getId());
        if (!optionalAcc.isPresent()) {
            return "redirect:/launchpad/account/accounts";
        }
        Account a = optionalAcc.get();

        if (StringUtils.isBlank(account.getPassword()) || StringUtils.isBlank(account.getPassword2())) {
            model.addAttribute("account", a);
            model.addAttribute("errorMessage", "#237.11 Both passwords must be not null");
            return "launchpad/account/account-password-edit-form";
        }

        if (!account.getPassword().equals(account.getPassword2())) {
            model.addAttribute("account", a);
            model.addAttribute("errorMessage", "#237.14 Both passwords must be equal");
            return "launchpad/account/account-password-edit-form";
        }
        a.setPassword(passwordEncoder.encode(account.getPassword()));
        accountRepository.save(a);

        return "redirect:/launchpad/account/accounts";
    }

/*
    @GetMapping("/account-delete/{id}")
    public String delete(@PathVariable Long id, Model model){
        Optional<Account> m = accountRepository.findById(id);
        if (!m.isPresent()) {
            return "redirect:launchpad/account/accounts";
        }
        model.addAttribute("account", m);
        return "launchpad/account/account-delete";
    }

    @PostMapping("/account-delete-commit")
    public String deleteCommit(Long id) {
        Optional<Account> m = accountRepository.findById(id);
        if (!m.isPresent()) {
            return "redirect:launchpad/account/accounts";
        }
        accountRepository.deleteById(id);
        return "redirect:/launchpad/account/accounts";
    }
*/

}

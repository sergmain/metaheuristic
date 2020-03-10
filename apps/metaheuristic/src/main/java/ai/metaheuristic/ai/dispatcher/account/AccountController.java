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

package ai.metaheuristic.ai.dispatcher.account;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.data.AccountData;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;

@SuppressWarnings("Duplicates")
@Controller
@RequestMapping("/dispatcher/account")
@Profile("dispatcher")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN')")
public class AccountController {

    private final AccountTopLevelService accountTopLevelService;
    private final UserContextService userContextService;

    @GetMapping("/accounts")
    public String accounts(Model model,
                           @ModelAttribute("result") AccountData.AccountsResult result,
                           @PageableDefault(size = 5) Pageable pageable,
                           @ModelAttribute("infoMessages") final ArrayList<String> infoMessages,
                           @ModelAttribute("errorMessage") final ArrayList<String> errorMessage, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        AccountData.AccountsResult accounts = accountTopLevelService.getAccounts(pageable, context);
        ControllerUtils.addMessagesToModel(model, accounts);
        model.addAttribute("result", accounts);
        return "dispatcher/account/accounts";
    }

    // for AJAX
    @PostMapping("/accounts-part")
    public String getAccountsViaAJAX(Model model, @PageableDefault(size=5) Pageable pageable, Authentication authentication )  {
        DispatcherContext context = userContextService.getContext(authentication);
        AccountData.AccountsResult accounts = accountTopLevelService.getAccounts(pageable, context);
        model.addAttribute("result", accounts);
        return "dispatcher/account/accounts :: table";
    }

    @GetMapping(value = "/account-add")
    public String add(@ModelAttribute("account") Account account) {
        return "dispatcher/account/account-add";
    }

    @PostMapping("/account-add-commit")
    public String addFormCommit(Model model, Account account, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        OperationStatusRest operationStatusRest = accountTopLevelService.addAccount(account, context);
        if (operationStatusRest.isErrorMessages()) {
            model.addAttribute("errorMessage", operationStatusRest.getErrorMessagesAsList());
            return "dispatcher/account/account-add";
        }
        return "redirect:/dispatcher/account/accounts";
    }

    @GetMapping(value = "/account-edit/{id}")
    public String edit(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, Authentication authentication){
        DispatcherContext context = userContextService.getContext(authentication);
        AccountData.AccountResult accountResult = accountTopLevelService.getAccount(id, context);
        if (accountResult.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", accountResult.getErrorMessagesAsList());
            return "redirect:/dispatcher/account/accounts";
        }
        model.addAttribute("account", accountResult.account);
        return "dispatcher/account/account-edit";
    }

    @PostMapping("/account-edit-commit")
    public String editFormCommit(Long id, String publicName, boolean enabled,
                                 final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        OperationStatusRest operationStatusRest = accountTopLevelService.editFormCommit(id, publicName, enabled, context);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.getErrorMessagesAsList());
        }
        if (operationStatusRest.isInfoMessages()) {
            redirectAttributes.addFlashAttribute("infoMessages", operationStatusRest.infoMessages);
        }
        return "redirect:/dispatcher/account/accounts";
    }

    @GetMapping(value = "/account-password-edit/{id}")
    public String passwordEdit(@PathVariable Long id, Model model,
                               final RedirectAttributes redirectAttributes, Authentication authentication){
        DispatcherContext context = userContextService.getContext(authentication);
        AccountData.AccountResult accountResult = accountTopLevelService.getAccount(id, context);
        if (accountResult.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", accountResult.getErrorMessagesAsList());
            return "redirect:/dispatcher/account/accounts";
        }
        model.addAttribute("account", accountResult.account);
        return "dispatcher/account/account-password-edit";
    }

    @PostMapping("/account-password-edit-commit")
    public String passwordEditFormCommit(Long id, String password, String password2,
                                         final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        OperationStatusRest operationStatusRest = accountTopLevelService.passwordEditFormCommit(id, password, password2, context);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.getErrorMessagesAsList());
        }
        if (operationStatusRest.isInfoMessages()) {
            redirectAttributes.addFlashAttribute("infoMessages", operationStatusRest.infoMessages);
        }
        return "redirect:/dispatcher/account/accounts";
    }
}

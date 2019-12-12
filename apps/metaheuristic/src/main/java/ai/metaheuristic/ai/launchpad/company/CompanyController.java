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

package ai.metaheuristic.ai.launchpad.company;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.launchpad.beans.Account;
import ai.metaheuristic.ai.launchpad.beans.Company;
import ai.metaheuristic.ai.launchpad.data.AccountData;
import ai.metaheuristic.ai.launchpad.data.CompanyData;
import ai.metaheuristic.ai.sec.SecConsts;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;

/**
 * @author Serge
 * Date: 10/27/2019
 * Time: 8:48 PM
 */

@SuppressWarnings("Duplicates")
@Controller
@RequestMapping("/launchpad/company")
@Profile("launchpad")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyTopLevelService companyTopLevelService;
    private final CompanyAccountTopLevelService companyAccountTopLevelService;

    @PreAuthorize("hasAnyRole('MASTER_ADMIN', 'MASTER_OPERATOR', 'MASTER_SUPPORT')")
    @GetMapping("/companies")
    public String companies(Model model,
                           @PageableDefault(size = CompanyTopLevelService.ROWS_IN_TABLE) Pageable pageable,
                           @ModelAttribute("infoMessages") final ArrayList<String> infoMessages,
                           @ModelAttribute("errorMessage") final ArrayList<String> errorMessage) {

        CompanyData.CompaniesResult companies = companyTopLevelService.getCompanies(pageable);
        ControllerUtils.addMessagesToModel(model, companies);
        model.addAttribute("result", companies);
        return "launchpad/company/companies";
    }

    // for AJAX
    @PostMapping("/companies-part")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN', 'MASTER_OPERATOR', 'MASTER_SUPPORT')")
    public String getCompaniesViaAJAX(Model model, @PageableDefault(size=CompanyTopLevelService.ROWS_IN_TABLE) Pageable pageable )  {
        CompanyData.CompaniesResult companies = companyTopLevelService.getCompanies(pageable);
        model.addAttribute("result", companies);
        return "launchpad/company/companies :: table";
    }

    @GetMapping(value = "/company-add")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String add(@ModelAttribute("company") Company company) {
        return "launchpad/company/company-add";
    }

    @PostMapping("/company-add-commit")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String addFormCommit(Model model, Company company) {
        OperationStatusRest operationStatusRest = companyTopLevelService.addCompany(company);
        if (operationStatusRest.isErrorMessages()) {
            model.addAttribute("errorMessage", operationStatusRest.errorMessages);
            return "launchpad/company/company-add";
        }
        return "redirect:/launchpad/company/companies";
    }

    @GetMapping(value = "/company-edit/{id}")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String editCompany(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes){
        CompanyData.CompanyResult companyResult = companyTopLevelService.getCompany(id);
        if (companyResult.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", companyResult.errorMessages);
            return "redirect:/launchpad/company/companies";
        }
        model.addAttribute("company", companyResult.company);
        model.addAttribute("companyYamlAsStr", companyResult.company.params);
        return "launchpad/company/company-edit";
    }

    @PostMapping("/company-edit-commit")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String editFormCommit(Long id, String name, String companyYamlAsStr, final RedirectAttributes redirectAttributes) {
        OperationStatusRest operationStatusRest = companyTopLevelService.editFormCommit(id, name, companyYamlAsStr);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
        }
        if (operationStatusRest.isInfoMessages()) {
            redirectAttributes.addFlashAttribute("infoMessages", operationStatusRest.infoMessages);
        }
        return "redirect:/launchpad/company/companies";
    }

    // === accounts for companies =====================

    @GetMapping("/company-accounts/{companyId}")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String accounts(Model model,
                           @ModelAttribute("result") AccountData.AccountsResult result,
                           @PageableDefault(size = 5) Pageable pageable, @PathVariable Long companyId,
                               @ModelAttribute("infoMessages") final ArrayList<String> infoMessages,
                           @ModelAttribute("errorMessage") final ArrayList<String> errorMessage) {
        AccountData.AccountsResult accounts = companyAccountTopLevelService.getAccounts(pageable, companyId);
        ControllerUtils.addMessagesToModel(model, accounts);
        model.addAttribute("result", accounts);
        model.addAttribute("companyId", companyId);
        return "launchpad/company/company-accounts";
    }

    // for AJAX
    @PostMapping("/company-accounts-part/{companyId}")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String getAccountsViaAJAX(Model model, @PageableDefault(size=5) Pageable pageable, @PathVariable Long companyId)  {
        AccountData.AccountsResult accounts = companyAccountTopLevelService.getAccounts(pageable, companyId);
        model.addAttribute("result", accounts);
        model.addAttribute("companyId", companyId);
        return "launchpad/company/company-accounts :: table";
    }

    @GetMapping(value = "/company-account-add/{companyId}")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String add(Model model, @ModelAttribute("account") Account account, @PathVariable Long companyId) {
        model.addAttribute("companyId", companyId);
        return "launchpad/company/company-account-add";
    }

    @PostMapping("/company-account-add-commit/{companyId}")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String addFormCommit(Model model, Account account, @PathVariable Long companyId) {
        account.companyId = companyId;
        OperationStatusRest operationStatusRest = companyAccountTopLevelService.addAccount(account, companyId);
        if (operationStatusRest.isErrorMessages()) {
            model.addAttribute("errorMessage", operationStatusRest.errorMessages);
            model.addAttribute("companyId", companyId);
            return "launchpad/company/company-account-add";
        }
        return "redirect:/launchpad/company/company-accounts/" + companyId;
    }

    @GetMapping(value = "/company-account-edit/{companyId}/{id}")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String edit(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, @PathVariable Long companyId){
        AccountData.AccountResult accountResult = companyAccountTopLevelService.getAccount(id, companyId);
        if (accountResult.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", accountResult.errorMessages);
            return "redirect:/launchpad/company/company-accounts/" + companyId;
        }
        accountResult.account.setPassword(null);
        accountResult.account.setPassword2(null);
        model.addAttribute("account", accountResult.account);
        model.addAttribute("companyId", companyId);
        return "launchpad/company/company-account-edit";
    }

    @PostMapping("/company-account-edit-commit/{companyId}")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String editFormCommit(Long id, String publicName, boolean enabled,
                                 final RedirectAttributes redirectAttributes, @PathVariable Long companyId) {
        OperationStatusRest operationStatusRest = companyAccountTopLevelService.editFormCommit(id, publicName, enabled, companyId);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
        }
        if (operationStatusRest.isInfoMessages()) {
            redirectAttributes.addFlashAttribute("infoMessages", operationStatusRest.infoMessages);
        }
        return "redirect:/launchpad/company/company-accounts/" + companyId;
    }

    @GetMapping(value = "/company-account-password-edit/{companyId}/{id}")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String passwordEdit(@PathVariable Long id, Model model,
                               final RedirectAttributes redirectAttributes, @PathVariable Long companyId){
        AccountData.AccountResult accountResult = companyAccountTopLevelService.getAccount(id, companyId);
        if (accountResult.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", accountResult.errorMessages);
            return "redirect:/launchpad/company/company-accounts/" + companyId;
        }
        accountResult.account.setPassword(null);
        accountResult.account.setPassword2(null);
        model.addAttribute("account", accountResult.account);
        model.addAttribute("companyId", companyId);
        return "launchpad/company/company-account-password-edit";
    }

    @PostMapping("/company-account-password-edit-commit/{companyId}")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String passwordEditFormCommit(Long id, String password, String password2,
                                         final RedirectAttributes redirectAttributes, @PathVariable Long companyId) {
        OperationStatusRest operationStatusRest = companyAccountTopLevelService.passwordEditFormCommit(id, password, password2, companyId);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
        }
        if (operationStatusRest.isInfoMessages()) {
            redirectAttributes.addFlashAttribute("infoMessages", operationStatusRest.infoMessages);
        }
        return "redirect:/launchpad/company/company-accounts/" + companyId;
    }

    @GetMapping(value = "/company-account-edit-roles/{companyId}/{id}")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String editRoles(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, @PathVariable Long companyId) {
        AccountData.AccountResult accountResult = companyAccountTopLevelService.getAccount(id, companyId);
        if (accountResult.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", accountResult.errorMessages);
            return "redirect:/launchpad/company/company-accounts/"+companyId;
        }
        accountResult.account.setPassword(null);
        accountResult.account.setPassword2(null);
        model.addAttribute("account", accountResult.account);
        model.addAttribute("roles", Consts.ID_1.equals(companyId) ? SecConsts.COMPANY_1_ROLES : SecConsts.POSSIBLE_ROLES);
        model.addAttribute("companyId", companyId);
        return "launchpad/company/company-account-edit-roles";
    }

    @PostMapping("/company-account-edit-roles-commit/{companyId}")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String rolesEditFormCommit(
            Long accountId, Integer roleId, @RequestParam(required = false, defaultValue = "false") boolean checkbox,
                                      final RedirectAttributes redirectAttributes, @PathVariable Long companyId) {
        AccountData.AccountResult accountResult = companyAccountTopLevelService.getAccount(accountId, companyId);
        if (accountResult.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", accountResult.errorMessages);
            return "redirect:/launchpad/company/company-accounts/"+companyId;
        }

        OperationStatusRest operationStatusRest = companyAccountTopLevelService.storeRolesForUserById(accountId, roleId, checkbox, companyId);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
        }
        if (operationStatusRest.isInfoMessages()) {
            redirectAttributes.addFlashAttribute("infoMessages", operationStatusRest.infoMessages);
        }
        return "redirect:/launchpad/company/company-account-edit-roles/"+companyId + "/" + accountId;
    }

}



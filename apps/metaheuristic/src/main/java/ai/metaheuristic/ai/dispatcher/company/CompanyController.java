/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.company;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.ai.dispatcher.data.AccountData;
import ai.metaheuristic.ai.dispatcher.data.CompanyData;
import ai.metaheuristic.ai.sec.SecConsts;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.S;
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

import static ai.metaheuristic.ai.dispatcher.company.CompanyTopLevelService.*;

/**
 * @author Serge
 * Date: 10/27/2019
 * Time: 8:48 PM
 */

@SuppressWarnings("Duplicates")
@Controller
@RequestMapping("/dispatcher/company")
@Profile("dispatcher")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyTopLevelService companyTopLevelService;
    private final CompanyAccountTopLevelService companyAccountTopLevelService;

    @PreAuthorize("hasAnyRole('MASTER_ADMIN', 'MASTER_OPERATOR', 'MASTER_SUPPORT')")
    @GetMapping("/companies")
    public String companies(
            Model model,
            @PageableDefault(size = ROWS_IN_TABLE) Pageable pageable,
            @ModelAttribute("infoMessages") final ArrayList<String> infoMessages,
            @ModelAttribute("errorMessage") final ArrayList<String> errorMessage) {

        CompanyData.SimpleCompaniesResult companies = companyTopLevelService.getCompanies(pageable);
        ControllerUtils.addMessagesToModel(model, companies);
        model.addAttribute("result", companies);
        return "dispatcher/company/companies";
    }

    // for AJAX
    @PostMapping("/companies-part")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN', 'MASTER_OPERATOR', 'MASTER_SUPPORT')")
    public String getCompaniesViaAJAX(Model model, @PageableDefault(size= ROWS_IN_TABLE) Pageable pageable)  {
        CompanyData.SimpleCompaniesResult companies = companyTopLevelService.getCompanies(pageable);
        model.addAttribute("result", companies);
        return "dispatcher/company/companies :: table";
    }

    @GetMapping(value = "/company-add")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String add(@ModelAttribute("company") Company company) {
        return "dispatcher/company/company-add";
    }

    @PostMapping("/company-add-commit")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String addFormCommit(Model model, Company company) {
        OperationStatusRest operationStatusRest = companyTopLevelService.addCompany(company);
        if (operationStatusRest.isErrorMessages()) {
            model.addAttribute("errorMessage", operationStatusRest.getErrorMessagesAsList());
            Company c = new Company();
            c.uniqueId = company.uniqueId;
            c.name = company.name;
            c.setParams( company.getParams() );
            model.addAttribute("company", company);
            return "dispatcher/company/company-add";
        }
        return "redirect:/dispatcher/company/companies";
    }

    @GetMapping(value = "/company-edit/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String editCompany(@PathVariable Long companyUniqueId, Model model, final RedirectAttributes redirectAttributes){
        CompanyData.SimpleCompanyResult companyResult = companyTopLevelService.getSimpleCompany(companyUniqueId);
        if (companyResult.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", companyResult.getErrorMessagesAsList());
            return "redirect:/dispatcher/company/companies";
        }
        model.addAttribute("company", companyResult.company);
        model.addAttribute("groups", S.b(companyResult.companyAccessControl.groups) ? "" : companyResult.companyAccessControl.groups );
        return "dispatcher/company/company-edit";
    }

    @PostMapping("/company-edit-commit")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String editFormCommit(Long companyUniqueId, String name, String groups, final RedirectAttributes redirectAttributes) {
        OperationStatusRest operationStatusRest = companyTopLevelService.editFormCommit(companyUniqueId, name, groups);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.getErrorMessagesAsList());
        }
        if (operationStatusRest.isInfoMessages()) {
            redirectAttributes.addFlashAttribute("infoMessages", operationStatusRest.infoMessages);
        }
        return "redirect:/dispatcher/company/companies";
    }

    // === accounts for companies =====================

    @GetMapping("/company-accounts/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String accounts(
            Model model,
            @ModelAttribute("result") AccountData.AccountsResult result,
            @PageableDefault(size = 5) Pageable pageable, @PathVariable Long companyUniqueId,
            @ModelAttribute("infoMessages") final ArrayList<String> infoMessages,
            @ModelAttribute("errorMessage") final ArrayList<String> errorMessage) {
        AccountData.AccountsResult accounts = companyAccountTopLevelService.getAccounts(pageable, companyUniqueId);
        ControllerUtils.addMessagesToModel(model, accounts);
        model.addAttribute("result", accounts);
        model.addAttribute("companyId", companyUniqueId);
        return "dispatcher/company/company-accounts";
    }

    // for AJAX
    @PostMapping("/company-accounts-part/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String getAccountsViaAJAX(Model model, @PageableDefault(size=5) Pageable pageable, @PathVariable Long companyUniqueId)  {
        AccountData.AccountsResult accounts = companyAccountTopLevelService.getAccounts(pageable, companyUniqueId);
        model.addAttribute("result", accounts);
        model.addAttribute("companyUniqueId", companyUniqueId);
        return "dispatcher/company/company-accounts :: table";
    }

    @GetMapping(value = "/company-account-add/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String add(Model model, @ModelAttribute("account") AccountData.NewAccount account, @PathVariable Long companyUniqueId) {
        model.addAttribute("companyUniqueId", companyUniqueId);
        return "dispatcher/company/company-account-add";
    }

    @PostMapping("/company-account-add-commit/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String addFormCommit(Model model, AccountData.NewAccount account, @PathVariable Long companyUniqueId) {
        OperationStatusRest operationStatusRest = companyAccountTopLevelService.addAccount(account, companyUniqueId);
        if (operationStatusRest.isErrorMessages()) {
            model.addAttribute("errorMessage", operationStatusRest.getErrorMessagesAsList());
            model.addAttribute("companyUniqueId", companyUniqueId);
            return "dispatcher/company/company-account-add";
        }
        return "redirect:/dispatcher/company/company-accounts/" + companyUniqueId;
    }

    @GetMapping(value = "/company-account-edit/{companyUniqueId}/{id}")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String edit(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, @PathVariable Long companyUniqueId){
        AccountData.AccountResult accountResult = companyAccountTopLevelService.getAccount(id, companyUniqueId);
        if (accountResult.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", accountResult.getErrorMessagesAsList());
            return "redirect:/dispatcher/company/company-accounts/" + companyUniqueId;
        }
        model.addAttribute("account", accountResult.account);
        model.addAttribute("companyUniqueId", companyUniqueId);
        return "dispatcher/company/company-account-edit";
    }

    @PostMapping("/company-account-edit-commit/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String editFormCommit(Long id, String publicName, boolean enabled,
                                 final RedirectAttributes redirectAttributes, @PathVariable Long companyUniqueId) {
        OperationStatusRest operationStatusRest = companyAccountTopLevelService.editFormCommit(id, publicName, enabled, companyUniqueId);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.getErrorMessagesAsList());
        }
        if (operationStatusRest.isInfoMessages()) {
            redirectAttributes.addFlashAttribute("infoMessages", operationStatusRest.infoMessages);
        }
        return "redirect:/dispatcher/company/company-accounts/" + companyUniqueId;
    }

    @GetMapping(value = "/company-account-password-edit/{companyUniqueId}/{id}")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String passwordEdit(@PathVariable Long id, Model model,
                               final RedirectAttributes redirectAttributes, @PathVariable Long companyUniqueId){
        AccountData.AccountResult accountResult = companyAccountTopLevelService.getAccount(id, companyUniqueId);
        if (accountResult.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", accountResult.getErrorMessagesAsList());
            return "redirect:/dispatcher/company/company-accounts/" + companyUniqueId;
        }
        model.addAttribute("account", accountResult.account);
        model.addAttribute("companyUniqueId", companyUniqueId);
        return "dispatcher/company/company-account-password-edit";
    }

    @PostMapping("/company-account-password-edit-commit/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String passwordEditFormCommit(Long id, String password, String password2,
                                         final RedirectAttributes redirectAttributes, @PathVariable Long companyUniqueId) {
        OperationStatusRest operationStatusRest = companyAccountTopLevelService.passwordEditFormCommit(id, password, password2, companyUniqueId);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.getErrorMessagesAsList());
        }
        if (operationStatusRest.isInfoMessages()) {
            redirectAttributes.addFlashAttribute("infoMessages", operationStatusRest.infoMessages);
        }
        return "redirect:/dispatcher/company/company-accounts/" + companyUniqueId;
    }

    /**
     *
     * @param id - Account.id
     * @param companyUniqueId - Account.companyId
     * @return
     */
    @GetMapping(value = "/company-account-edit-roles/{companyUniqueId}/{id}")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String editRoles(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, @PathVariable Long companyUniqueId) {
        AccountData.AccountResult accountResult = companyAccountTopLevelService.getAccount(id, companyUniqueId);
        if (accountResult.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", accountResult.getErrorMessagesAsList());
            return "redirect:/dispatcher/company/company-accounts/"+companyUniqueId;
        }
        model.addAttribute("account", accountResult.account);
        model.addAttribute("roles", Consts.ID_1.equals(companyUniqueId) ? SecConsts.COMPANY_1_POSSIBLE_ROLES : SecConsts.POSSIBLE_ROLES);
        model.addAttribute("companyUniqueId", companyUniqueId);
        return "dispatcher/company/company-account-edit-roles";
    }

    /**
     * !!! this method accepts an index(roleIndex) in an array of possible roles
     */
    @PostMapping("/company-account-edit-roles-commit/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String rolesEditFormCommit(
            Long accountId, String role, @RequestParam(required = false, defaultValue = "false") boolean checkbox,
                                      final RedirectAttributes redirectAttributes, @PathVariable Long companyUniqueId) {
        AccountData.AccountResult accountResult = companyAccountTopLevelService.getAccount(accountId, companyUniqueId);
        if (accountResult.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", accountResult.getErrorMessagesAsList());
            return "redirect:/dispatcher/company/company-accounts/"+companyUniqueId;
        }
        OperationStatusRest operationStatusRest = companyAccountTopLevelService.storeRolesForUserById(accountId, role, checkbox, companyUniqueId);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.getErrorMessagesAsList());
        }
        if (operationStatusRest.isInfoMessages()) {
            redirectAttributes.addFlashAttribute("infoMessages", operationStatusRest.infoMessages);
        }
        return "redirect:/dispatcher/company/company-account-edit-roles/"+companyUniqueId + "/" + accountId;
    }

}



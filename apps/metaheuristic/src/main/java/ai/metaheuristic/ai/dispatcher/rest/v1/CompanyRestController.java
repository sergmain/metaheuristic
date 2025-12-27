/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.rest.v1;

import ai.metaheuristic.ai.dispatcher.company.CompanyAccountTopLevelService;
import ai.metaheuristic.ai.dispatcher.company.CompanyTopLevelService;
import ai.metaheuristic.ai.dispatcher.data.AccountData;
import ai.metaheuristic.ai.dispatcher.data.CompanyData;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static ai.metaheuristic.ai.dispatcher.company.CompanyTopLevelService.ROWS_IN_TABLE;

/**
 * @author Serge
 * Date: 9/3/2020
 * Time: 11:37 AM
 */
@SuppressWarnings("Duplicates")
@RestController
@RequestMapping("/rest/v1/dispatcher/company")
@Slf4j
@Profile("dispatcher")
@CrossOrigin
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class CompanyRestController {

    private final CompanyTopLevelService companyTopLevelService;
    private final CompanyAccountTopLevelService companyAccountTopLevelService;

    @PreAuthorize("hasAnyRole('MAIN_ADMIN', 'MAIN_OPERATOR', 'MAIN_SUPPORT')")
    @GetMapping("/companies")
    public CompanyData.SimpleCompaniesResult companies(@PageableDefault(size = ROWS_IN_TABLE) Pageable pageable) {
        CompanyData.SimpleCompaniesResult companies = companyTopLevelService.getCompanies(pageable);
        return companies;
    }

    @PostMapping("/company-add-commit")
    @PreAuthorize("hasAnyRole('MAIN_ADMIN')")
    public OperationStatusRest addFormCommit(String companyName) {
        OperationStatusRest operationStatusRest = companyTopLevelService.addCompany(companyName);
        return operationStatusRest;
    }

    @GetMapping(value = "/company-edit/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MAIN_ADMIN')")
    public CompanyData.SimpleCompanyResult editCompany(@PathVariable Long companyUniqueId){
        CompanyData.SimpleCompanyResult companyResult = companyTopLevelService.getSimpleCompany(companyUniqueId);
        return companyResult;
    }

    @PostMapping("/company-edit-commit")
    @PreAuthorize("hasAnyRole('MAIN_ADMIN')")
    public OperationStatusRest editFormCommit(Long companyUniqueId, String name, String groups) {
        OperationStatusRest operationStatusRest = companyTopLevelService.editFormCommit(companyUniqueId, name, groups);
        return operationStatusRest;
    }

    // === accounts for companies =====================

    @GetMapping("/company-accounts/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MAIN_ADMIN')")
    public AccountData.AccountsResult accounts(@PageableDefault(size = 5) Pageable pageable, @PathVariable Long companyUniqueId) {
        AccountData.AccountsResult accounts = companyAccountTopLevelService.getAccounts(pageable, companyUniqueId);
        return accounts;
    }

    @PostMapping("/company-account-add-commit/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MAIN_ADMIN')")
    public OperationStatusRest addFormCommit(@RequestBody AccountData.NewAccount account, @PathVariable Long companyUniqueId) {
        OperationStatusRest operationStatusRest = companyAccountTopLevelService.addAccount(account, companyUniqueId);
        return operationStatusRest;
    }

    @GetMapping(value = "/company-account-edit/{companyUniqueId}/{id}")
    @PreAuthorize("hasAnyRole('MAIN_ADMIN')")
    public AccountData.AccountResult edit(@PathVariable Long id, @PathVariable Long companyUniqueId){
        AccountData.AccountResult accountResult = companyAccountTopLevelService.getAccount(id, companyUniqueId);
        return accountResult;
    }

    @PostMapping("/company-account-edit-commit/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MAIN_ADMIN')")
    public OperationStatusRest editFormCommit(@Nullable Long id, @Nullable String publicName, boolean enabled, @Nullable @PathVariable Long companyUniqueId) {
        OperationStatusRest operationStatusRest = companyAccountTopLevelService.editFormCommit(id, publicName, enabled, companyUniqueId);
        return operationStatusRest;
    }

    @GetMapping(value = "/company-account-password-edit/{companyUniqueId}/{id}")
    @PreAuthorize("hasAnyRole('MAIN_ADMIN')")
    public AccountData.AccountResult passwordEdit(@PathVariable Long id, @PathVariable Long companyUniqueId){
        AccountData.AccountResult accountResult = companyAccountTopLevelService.getAccount(id, companyUniqueId);
        return accountResult;
    }

    @PostMapping("/company-account-password-edit-commit/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MAIN_ADMIN')")
    public OperationStatusRest passwordEditFormCommit(Long id, String password, String password2, @PathVariable Long companyUniqueId) {
        OperationStatusRest operationStatusRest = companyAccountTopLevelService.passwordEditFormCommit(id, password, password2, companyUniqueId);
        return operationStatusRest;
    }

    @GetMapping(value = "/company-account-edit-roles/{companyUniqueId}/{id}")
    @PreAuthorize("hasAnyRole('MAIN_ADMIN')")
    public AccountData.AccountWithRoleResult editRoles(@PathVariable Long id, @PathVariable Long companyUniqueId) {
        AccountData.AccountWithRoleResult accountWithRole = companyAccountTopLevelService.getAccountWithRole(id, companyUniqueId);
        return accountWithRole;
    }

    /**
     *
     * @param accountId Account.id
     * @param role name of role to set or remove
     * @param checkbox flag to set a role or to remove it
     * @param companyUniqueId Account.companyId
     * @return see @ai.metaheuristic.api.data.OperationStatusRest
     */
    @PostMapping("/company-account-edit-roles-commit/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MAIN_ADMIN')")
    public OperationStatusRest rolesEditFormCommit(Long accountId, String role, @RequestParam(required = false, defaultValue = "false") boolean checkbox,
                                      @PathVariable Long companyUniqueId) {
        AccountData.AccountResult accountResult = companyAccountTopLevelService.getAccount(accountId, companyUniqueId);
        if (accountResult.isErrorMessages()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, accountResult.getErrorMessages(), accountResult.infoMessages);
        }
        OperationStatusRest operationStatusRest = companyAccountTopLevelService.storeRolesForUserById(accountId, role, checkbox, companyUniqueId);
        return operationStatusRest;
    }


}

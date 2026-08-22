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

package ai.metaheuristic.ai.dispatcher.account;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.beans.AccountRevision;
import ai.metaheuristic.ai.dispatcher.data.AccountData;
import ai.metaheuristic.ai.dispatcher.repositories.AccountRepository;
import ai.metaheuristic.ai.dispatcher.repositories.AccountRevisionRepository;
import ai.metaheuristic.ai.sec.RoleService;
import ai.metaheuristic.ai.sec.SecConsts;
import ai.metaheuristic.ai.yaml.account.AccountParamsYaml;
import ai.metaheuristic.ai.yaml.account.AccountParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.account.SimpleAccount;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.account.UserContext;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 10/30/2019
 * Time: 1:21 AM
 */
@Service
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class AccountTxService {

    private final Globals globals;
    private final AccountRepository accountRepository;
    private final AccountRevisionRepository accountRevisionRepository;
    private final AccountCache accountCache;
    private final AccountRevisionWriter accountRevisionWriter;
    private final PasswordEncoder passwordEncoder;
    private final RoleService roleService;

    @Nullable
    @Transactional(readOnly = true)
    public Account findByUsername(String username) {
        return accountCache.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public AccountData.AccountsResult getAccounts(Pageable pageable, Long companyUniqueId)  {
        AccountData.AccountsResult result = new AccountData.AccountsResult();
        result.accounts = accountRepository.findAllByCompanyUniqueId(pageable, companyUniqueId);
        result.assetMode = globals.dispatcher.asset.mode;
        return result;
    }

    /**
     * Create an account whose ROLE SET is owned by a MECHANISM rather than by a
     * human administrator.
     *
     * <p>The admin-assignment gate in {@link #addAccount} is skipped here on
     * purpose: that gate exists to stop a HUMAN handing out a mechanism-managed
     * role, and the caller of this method IS the mechanism. Skipping it is the
     * whole distinction between the two entry points — not a loophole, because
     * this method is unreachable from any controller.
     *
     * <p>The account is stamped {@code managedBy}, so from this moment no admin
     * path can edit its roles in either direction.
     */
    @Transactional
    public OperationStatusRest addAccountManagedBy(
            AccountData.NewAccount acc, Long companyUniqueId, String roles, EnumsApi.RoleManager manager) {
        if (!manager.server) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "235.180 addAccountManagedBy requires a mechanism, not 'admin'");
        }
        return addAccountInternal(acc, companyUniqueId, roles, manager);
    }

    @Transactional
    public OperationStatusRest addAccount(AccountData.NewAccount acc, Long companyUniqueId, String roles) {
        return addAccountInternal(acc, companyUniqueId, roles, EnumsApi.RoleManager.admin);
    }

    private OperationStatusRest addAccountInternal(
            AccountData.NewAccount acc, Long companyUniqueId, String roles, EnumsApi.RoleManager manager) {

        if (StringUtils.isBlank(acc.getUsername()) ||
                StringUtils.isBlank(acc.getPassword()) ||
                StringUtils.isBlank(acc.getPassword2()) ||
                StringUtils.isBlank(acc.getPublicName())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "235.010 Username, roles, password, and public name must be not null");
        }
        if (acc.getUsername().indexOf('=')!=-1 ) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "235.020 Username can't contain '='");
        }
        if (!acc.getPassword().equals(acc.getPassword2())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "235.030 Both passwords must be equal");
        }

        final Account byUsername = accountRepository.findByUsername(acc.getUsername());
        if (byUsername !=null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    String.format("235.040 Username '%s' was already registered", acc.getUsername()));
        }

        // Envelope draft — identity + security primitives. PUBLIC_NAME goes on the first revision.
        Account envelopeDraft = new Account();
        envelopeDraft.username = acc.username;
        envelopeDraft.password = passwordEncoder.encode(acc.password);
        // Initial grant is the other way a role reaches an account, so it needs the
        // same gate. Without it an admin could create a fresh account carrying the
        // managed role — indistinguishable from a real one afterwards, with no
        // record of where it came from.
        if (!manager.server) {
            for (String r : StringUtils.split(roles==null ? "" : roles, ',')) {
                final String trimmed = r.trim();
                if (!trimmed.isEmpty() && !roleService.isAssignableByAdmin(trimmed)) {
                    return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                            "235.160 Role " + trimmed + " is granted by '" + roleService.getRoleManager(trimmed)
                                    + "' and can't be assigned by hand");
                }
            }
        }
        else {
            // Every role granted this way must belong to THIS mechanism; a mechanism
            // may not use its own entry point to hand out somebody else's role.
            // Asking for the server flag rather than manager identity so a role that is
            // BOTH minted here and grantable by hand still passes; with one mechanism in
            // existence the two readings coincide, and a second would need the owner named.
            for (String r : StringUtils.split(roles==null ? "" : roles, ',')) {
                final String trimmed = r.trim();
                if (!trimmed.isEmpty() && !roleService.getRoleManager(trimmed).server) {
                    return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                            "235.190 Role " + trimmed + " isn't managed by '" + manager + "'");
                }
            }
            envelopeDraft.managedBy = manager.name();
        }

        envelopeDraft.roles = roles;
        envelopeDraft.accountNonExpired = true;
        envelopeDraft.accountNonLocked = true;
        envelopeDraft.credentialsNonExpired = true;
        envelopeDraft.enabled = true;
        envelopeDraft.companyId = companyUniqueId;
        envelopeDraft.createdOn = System.currentTimeMillis();

        AccountRevisionWriter.ProfilePayload profile = new AccountRevisionWriter.ProfilePayload(
                acc.publicName, null, null, null, null, false, null);
        accountRevisionWriter.create(envelopeDraft, profile);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional(readOnly = true)
    public AccountData.AccountResult getAccount(Long id, Long companyUniqueId){
        Account account = accountRepository.findById(id).orElse(null);
        if (account == null || !Objects.equals(account.companyId, companyUniqueId)) {
            return new AccountData.AccountResult("235.050 account wasn't found, accountId: " + id);
        }
        AccountRevision head = account.headRevisionId == null
                ? null
                : accountRevisionRepository.findById(account.headRevisionId).orElse(null);
        return new AccountData.AccountResult(toSimple(account, head));
    }

    private static SimpleAccount toSimple(Account acc, @Nullable AccountRevision head) {
        String publicName = head != null ? head.publicName : "";
        long updatedOn = head != null ? head.updatedOn : 0L;
        return new SimpleAccount(acc.id, acc.companyId, acc.username, publicName, acc.enabled, acc.createdOn, updatedOn, acc.accountRoles.asString());
    }

    @Transactional
    public OperationStatusRest editFormCommit(@Nullable Long accountId, @Nullable String publicName, boolean enabled, @Nullable Long companyUniqueId) {
        if (accountId==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"235.055 System error, accountId is null");
        }
        if (S.b(publicName)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"235.056 System error, publicName is blank");
        }
        if (companyUniqueId==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"235.057 System error, companyUniqueId is null");
        }
        Account a = accountRepository.findByIdForUpdate(accountId);
        if (a == null || !Objects.equals(a.companyId, companyUniqueId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"235.060 account wasn't found, accountId: " + accountId);
        }
        // IS_ENABLED lives on the envelope (Spring-Security primitive) — envelope-only write.
        accountRevisionWriter.updateEnabled(accountId, enabled);
        // PUBLIC_NAME lives on the satellite — new revision carrying current fields with new publicName.
        AccountRevision currentHead = a.headRevisionId == null
                ? null
                : accountRevisionRepository.findById(a.headRevisionId).orElse(null);
        accountRevisionWriter.writeNewRevision(accountId, profilePayloadFrom(currentHead, publicName));
        return new OperationStatusRest(EnumsApi.OperationStatus.OK,"The data of account was changed successfully", "");
    }

    @Transactional
    public OperationStatusRest passwordEditFormCommit(Long accountId, String password, String password2, Long companyUniqueId) {
        if (StringUtils.isBlank(password) || StringUtils.isBlank(password2)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "235.080 Both passwords must be not null");
        }

        if (!password.equals(password2)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "235.090 Both passwords must be equal");
        }
        Account a = accountRepository.findByIdForUpdate(accountId);
        if (a == null || !Objects.equals(a.companyId, companyUniqueId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "235.100 account wasn't found, accountId: " + accountId);
        }
        // PASSWORD is an envelope-resident Spring-Security primitive.
        accountRevisionWriter.updatePassword(accountId, passwordEncoder.encode(password));

        return new OperationStatusRest(EnumsApi.OperationStatus.OK,"The password was changed successfully", "");
    }

    // this method is using with angular's rest
    @Transactional
    public OperationStatusRest roleFormCommit(Long accountId, String roles, Long companyUniqueId) {
        Account account = accountRepository.findByIdForUpdate(accountId);
        if (account == null || !Objects.equals(account.companyId, companyUniqueId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"235.110 account wasn't found, accountId: " + accountId);
        }
        // Wholesale replacement of the role set, so the mechanism gate applies here
        // exactly as it does to the per-role toggle.
        if (account.managedBy!=null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "235.170 Account's roles are managed by '" + account.managedBy
                            + "' and can't be edited by hand, accountId: " + accountId);
        }

        List<String> possibleRoles = roleService.getPossibleRoles();
        String str = Arrays.stream(StringUtils.split(roles, ','))
                .map(String::strip)
                .filter(possibleRoles::contains)
                // A managed role stays in possibleRoles by design, so membership alone
                // would let this path hand one out. Filtered here instead of refused,
                // matching how this method already treats an unknown role.
                .filter(roleService::isAssignableByAdmin)
                .collect(Collectors.joining(", "));

        // ROLES lives on the envelope.
        accountRevisionWriter.updateRoles(accountId, str);
        return new OperationStatusRest(EnumsApi.OperationStatus.OK,"The data of account was changed successfully", "");
    }

    /**
     * Toggle ONE role on an account, on behalf of an ADMIN of {@code callerCompanyId}.
     *
     * <p>Separate from {@link #storeRolesForUserById} rather than a parameterisation of it,
     * because the two differ in the one place that matters. That method serves a MAIN_ADMIN,
     * who sees the account's whole role universe, and it strips any held role missing from
     * that universe. An ADMIN sees only the regular universe, which for a management-company account
     * omits every {@code ROLE_MAIN_*} the account legitimately holds — running the same strip
     * loop would turn a toggle of {@code ROLE_OPERATOR} into a silent demotion of roles the
     * ADMIN was never shown. Here the named role is the only one touched.
     *
     * <p>The company is the CALLER's, resolved upstream from the authentication principal and
     * never accepted from the request, so an ADMIN cannot aim this at another company's account.
     */
    @Transactional
    public OperationStatusRest storeRoleForUserByIdWithinCompany(Long accountId, String role, boolean checkbox, @Nullable Long callerCompanyId) {
        Account account = accountRepository.findByIdForUpdate(accountId);
        if (account == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "235.200 account wasn't found, accountId: " + accountId);
        }
        AccountRoleEditUtils.Verdict verdict = AccountRoleEditUtils.validateToggle(
                account.companyId, callerCompanyId, account.managedBy, role,
                roleService.getPossibleRoles(), roleService::isAssignableByAdmin);
        if (!verdict.allowed()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, verdict.error());
        }

        List<String> newRoles = AccountRoleEditUtils.toggle(account.accountRoles.getRolesAsList(), role, checkbox);

        // ROLES lives on the envelope.
        accountRevisionWriter.updateRoles(accountId, String.join(", ", newRoles));
        return new OperationStatusRest(EnumsApi.OperationStatus.OK, "Role " + role + " was changed successfully", "");
    }

    // this method is for using with company-accounts
    @Transactional
    public OperationStatusRest storeRolesForUserById(Long accountId, String role, boolean checkbox, Long companyUniqueId) {
        Account account = accountRepository.findByIdForUpdate(accountId);
        if (account == null || !Objects.equals(account.companyId, companyUniqueId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"235.120 account wasn't found, accountId: " + accountId);
        }

        List<String> possibleRoles = Consts.ID_1.equals(companyUniqueId) ? roleService.getManagementCompanyPossibleRoles() : roleService.getPossibleRoles();
        if (!possibleRoles.contains(role)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"235.130 account wasn't found, accountId: " + accountId);
        }

        // The account's role set belongs to a mechanism, so no hand-editing of it —
        // in either direction. This is what stops a mechanism-owned account being
        // widened with an unrelated role while its own role sits untouched.
        if (account.managedBy!=null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "235.140 Account's roles are managed by '" + account.managedBy
                            + "' and can't be edited by hand, accountId: " + accountId);
        }
        // The role itself is granted by a mechanism, never handed out by a human.
        // Checked SEPARATELY from list membership above: a managed role must stay in
        // possibleRoles, or the strip loop below would delete it from every account
        // that legitimately holds it on the next unrelated toggle.
        if (!roleService.isAssignableByAdmin(role)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "235.150 Role " + role + " is granted by '" + roleService.getRoleManager(role)
                            + "' and can't be assigned by hand");
        }

        List<String> currRoles = account.accountRoles.getRolesAsList();
        for (String currRole : currRoles) {
            if (!possibleRoles.contains(currRole)) {
                account.accountRoles.removeRole(currRole);
            }
        }

        boolean isAccountContainsRole = account.accountRoles.hasRole(role);
        if (isAccountContainsRole && !checkbox){
            account.accountRoles.removeRole(role);
        } else if (!isAccountContainsRole && checkbox) {
            account.accountRoles.addRole(role);
        }

        if (!Consts.ID_1.equals(account.getCompanyId())) {
            account.accountRoles.removeRole(SecConsts.ROLE_SERVER_REST_ACCESS);
        }

        // accountRoles mutations write through to the envelope's `roles` field via the AccountRoles setter lambda.
        accountRevisionWriter.updateRoles(accountId, account.accountRoles.asString());
        return new OperationStatusRest(EnumsApi.OperationStatus.OK, "Role "+role+" was changed successfully", "");
    }

    @Transactional
    public OperationStatusRest changePasswordCommit(String oldPassword, String newPassword, UserContext context) {
        Account a = accountRepository.findByIdForUpdate(context.getAccountId());
        if (a == null || !Objects.equals(a.companyId, context.getCompanyId())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "235.310 account wasn't found, accountId: " + context.getAccountId());
        }

        if (!passwordEncoder.matches(oldPassword, a.getPassword())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "235.330 Old password is wrong");
        }

        // PASSWORD is an envelope-resident Spring-Security primitive.
        accountRevisionWriter.updatePassword(a.id, passwordEncoder.encode(newPassword));

        return new OperationStatusRest(EnumsApi.OperationStatus.OK,"The password was changed successfully", "");
    }

    @Transactional
    public OperationStatusRest saveOpenaiKey(Long accountId, Long companyId, String openaiKey) {
        return updateAccountParams(accountId, companyId, p -> p.openaiKey=openaiKey, "The OPEN_API_KEY was saved successfully");
    }

    @Transactional
    public OperationStatusRest saveAnthropicKey(Long accountId, Long companyId, String anthropicKey) {
        return updateAccountParams(accountId, companyId, p -> p.anthropicKey=anthropicKey, "The ANTHROPIC_API_KEY was saved successfully");
    }

    @Transactional
    public OperationStatusRest setLanguage(Long accountId, Long companyId, String lang) {
        return updateAccountParams(accountId, companyId, p -> p.language=lang, "Language was changed");
    }

    @Transactional
    public OperationStatusRest resetLanguage(Long accountId, Long companyId) {
        return updateAccountParams(accountId, companyId, p -> p.language="en", "Language was reset to 'en'");
    }

    private OperationStatusRest updateAccountParams(Long accountId, Long companyId, Consumer<AccountParamsYaml> updateFunc, String okMessage) {

        Account account = accountRepository.findByIdForUpdate(accountId);
        if (account == null || !Objects.equals(account.companyId, companyId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"235.360 account wasn't found, accountId: " + accountId);
        }
        // PARAMS lives on the satellite — pull current head, mutate, and INSERT new revision.
        AccountRevision currentHead = account.headRevisionId == null
                ? null
                : accountRevisionRepository.findById(account.headRevisionId).orElse(null);
        AccountParamsYaml params = currentHead != null
                ? currentHead.getAccountParamsYaml()
                : new AccountParamsYaml();
        updateFunc.accept(params);
        String paramsYaml = AccountParamsYamlUtils.UTILS.toString(params);

        AccountRevisionWriter.ProfilePayload payload = new AccountRevisionWriter.ProfilePayload(
                currentHead != null ? currentHead.publicName : "",
                currentHead != null ? currentHead.mailAddress : null,
                currentHead != null ? currentHead.phone : null,
                currentHead != null ? currentHead.phoneAsStr : null,
                currentHead != null ? currentHead.secretKey : null,
                currentHead != null && currentHead.twoFA,
                paramsYaml
        );
        accountRevisionWriter.writeNewRevision(accountId, payload);

        return new OperationStatusRest(EnumsApi.OperationStatus.OK,okMessage, "");
    }

    /**
     * Build a profile payload from the existing head revision, overriding only the publicName.
     * Used by editFormCommit which changes only PUBLIC_NAME on the satellite.
     */
    private static AccountRevisionWriter.ProfilePayload profilePayloadFrom(@Nullable AccountRevision head, String newPublicName) {
        if (head == null) {
            return new AccountRevisionWriter.ProfilePayload(newPublicName, null, null, null, null, false, null);
        }
        return new AccountRevisionWriter.ProfilePayload(
                newPublicName,
                head.mailAddress,
                head.phone,
                head.phoneAsStr,
                head.secretKey,
                head.twoFA,
                head.getParams()
        );
    }
}

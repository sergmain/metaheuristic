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

package ai.metaheuristic.ai.dispatcher.comm_channel;

import ai.metaheuristic.ai.dispatcher.account.AccountTxService;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.beans.CommChannel;
import ai.metaheuristic.ai.dispatcher.data.AccountData;
import ai.metaheuristic.ai.dispatcher.repositories.AccountRepository;
import ai.metaheuristic.ai.dispatcher.repositories.CommChannelRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.account.CommChannelServiceProvider.CommChannelService;
import ai.metaheuristic.commons.account.RoleManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issuing, activating and revoking communication channels.
 *
 * <p>A channel is an authenticated account bound to one REST endpoint.
 * Purpose-agnostic: the service tag and the role it carries come from the
 * registry, and nothing here interprets either. <b>What the outside party does
 * with the credential is out of scope.</b>
 *
 * <p><b>Activation discloses no reason.</b> {@link CommChannelTokenUtils} tells
 * apart notFound / withdrawn / alreadyActivated / expired, and this service logs
 * the distinction for the operator — but every refusal returns the same opaque
 * message. A caller holding a guessed token that learns "expired" rather than
 * "not found" has learned the token was real, which turns a refusal into an
 * oracle for enumerating valid tokens.
 *
 * <p>Error code prefix: {@code 01.238.} (unique to this class).
 *
 * @author Sergio Lissner
 * Date: 8/2/2026
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class CommChannelTxService {

    /** The single refusal every failed activation returns, whatever the cause. */
    public static final String ACTIVATION_REFUSED = "Token is not valid";

    private final CommChannelRepository commChannelRepository;
    private final AccountRepository accountRepository;
    private final AccountTxService accountTxService;

    /**
     * Issue a token bound to a service.
     *
     * <p>{@code grantedRole} is copied from the registry NOW rather than looked
     * up at activation, so what a token in someone's hands is worth is fixed at
     * the moment the operator decided it. A registry edited in between would
     * otherwise silently change the grant.
     */
    @Transactional
    public CommChannelData.IssuedChannel issue(
            Long companyUniqueId, CommChannelService service, Long createdByAccountId,
            @Nullable String intendedFor, @Nullable String description, long ttlMillis) {

        if (ttlMillis <= 0) {
            return CommChannelData.IssuedChannel.error(
                    "01.238.040 ttlMillis must be positive, actual: " + ttlMillis);
        }

        final long now = System.currentTimeMillis();

        CommChannel channel = new CommChannel();
        channel.companyId = companyUniqueId;
        channel.serviceTag = service.tag();
        channel.grantedRole = service.role();
        channel.token = CommChannelTokenUtils.newToken();
        channel.intendedFor = intendedFor;
        channel.description = description;
        channel.createdOn = now;
        channel.expiredOn = now + ttlMillis;
        channel.createdByAccountId = createdByAccountId;
        channel.deleted = false;

        commChannelRepository.save(channel);

        return new CommChannelData.IssuedChannel(
                channel.id, channel.token, channel.serviceTag, channel.expiredOn);
    }

    /**
     * Activate a token: mint the account, assign the bound role, return the
     * credential once.
     *
     * <p>Username and password are both GENERATED. This credential is used by a
     * machine over HTTP Basic, so there is no human memorability to trade
     * against entropy, and letting an unauthenticated caller choose either would
     * put the account's identifier and strength in the hands of whoever happened
     * to hold the token.
     */
    @Transactional
    public CommChannelData.ActivatedChannel activate(String token) {
        if (StringUtils.isBlank(token)) {
            return CommChannelData.ActivatedChannel.error(ACTIVATION_REFUSED);
        }

        final CommChannel channel = commChannelRepository.findByTokenForUpdate(token);
        final long now = System.currentTimeMillis();

        final String refusal = CommChannelTokenUtils.activationRefusalReason(channel, now);
        if (refusal!=null || channel==null) {
            // logged for the operator, never returned to the caller
            log.warn("01.238.060 channel activation refused, reason: {}", refusal);
            return CommChannelData.ActivatedChannel.error(ACTIVATION_REFUSED);
        }

        final String username = CommChannelTokenUtils.newUsername();
        final String rawPassword = CommChannelTokenUtils.newPassword();

        AccountData.NewAccount newAccount = new AccountData.NewAccount();
        newAccount.username = username;
        newAccount.password = rawPassword;
        newAccount.password2 = rawPassword;
        newAccount.publicName = channel.description==null ? channel.serviceTag : channel.description;

        final OperationStatusRest status = accountTxService.addAccountManagedBy(
                newAccount, channel.companyId, channel.grantedRole, RoleManager.commChannel);
        if (status.status!=EnumsApi.OperationStatus.OK) {
            log.error("01.238.080 account creation failed during channel activation: {}",
                    status.getErrorMessagesAsStr());
            return CommChannelData.ActivatedChannel.error(ACTIVATION_REFUSED);
        }

        final Account account = accountRepository.findByUsername(username);
        if (account==null) {
            log.error("01.238.100 account was reported created but not found, username: {}", username);
            return CommChannelData.ActivatedChannel.error(ACTIVATION_REFUSED);
        }

        // Marking the token spent in the SAME transaction as account creation is
        // what makes single-use real: a crash between the two would otherwise leave
        // a live token that had already minted an account.
        channel.activatedOn = now;
        channel.accountId = account.id;
        commChannelRepository.save(channel);

        return new CommChannelData.ActivatedChannel(
                username, rawPassword, account.id, channel.companyId);
    }

    /**
     * Withdraw an unactivated token, or close an activated channel.
     *
     * <p>Closing DISABLES the account rather than deleting it: the channel row
     * and the account survive for audit, and disabling is exactly the lever
     * {@code managedBy} deliberately leaves available to an operator.
     */
    @Transactional
    public OperationStatusRest revoke(CommChannel channel) {
        channel.deleted = true;
        commChannelRepository.save(channel);

        if (channel.accountId!=null) {
            final Account account = accountRepository.findById(channel.accountId).orElse(null);
            if (account!=null) {
                account.enabled = false;
                accountRepository.save(account);
            }
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }
}

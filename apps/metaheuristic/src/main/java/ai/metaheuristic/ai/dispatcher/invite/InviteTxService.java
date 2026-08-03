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

package ai.metaheuristic.ai.dispatcher.invite;

import ai.metaheuristic.ai.dispatcher.account.AccountTxService;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.beans.Invite;
import ai.metaheuristic.ai.dispatcher.data.AccountData;
import ai.metaheuristic.ai.dispatcher.repositories.AccountRepository;
import ai.metaheuristic.ai.dispatcher.repositories.InviteRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creation and redemption of single-use {@link Invite} tokens.
 *
 * <p>Purpose-agnostic: {@code roles} arrives from the caller and is passed to
 * {@link AccountTxService#addAccount} untouched. Nothing here knows or asks
 * what any role means.
 *
 * <p><b>Redemption discloses no reason.</b> {@link InviteTokenUtils} tells
 * apart notFound / withdrawn / alreadyRedeemed / expired, and this service logs
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
public class InviteTxService {

    /** The single refusal every failed redemption returns, whatever the cause. */
    public static final String REDEMPTION_REFUSED = "Invite token is not valid";

    private final InviteRepository inviteRepository;
    private final AccountRepository accountRepository;
    private final AccountTxService accountTxService;

    @Transactional
    public InviteData.CreatedInvite createInvite(
            Long companyUniqueId, String roles, Long createdByAccountId,
            @Nullable String description, long ttlMillis) {

        if (StringUtils.isBlank(roles)) {
            return InviteData.CreatedInvite.error("01.238.020 roles must not be blank");
        }
        if (ttlMillis <= 0) {
            return InviteData.CreatedInvite.error("01.238.040 ttlMillis must be positive, actual: " + ttlMillis);
        }

        final long now = System.currentTimeMillis();

        Invite invite = new Invite();
        invite.companyId = companyUniqueId;
        invite.token = InviteTokenUtils.newToken();
        invite.roles = roles;
        invite.description = description;
        invite.createdOn = now;
        invite.expiredOn = now + ttlMillis;
        invite.createdByAccountId = createdByAccountId;
        invite.deleted = false;

        inviteRepository.save(invite);

        return new InviteData.CreatedInvite(true, null, invite.id, invite.token, invite.expiredOn);
    }

    /**
     * Redeem a token, minting one account in the invite's company carrying the
     * invite's roles.
     *
     * <p>The generated username is random rather than caller-supplied: it is
     * globally unique by construction, so it can never collide with
     * {@code mh_account_username_unq_idx}, and an unauthenticated caller never
     * gets to choose an identifier that other people will see.
     */
    @Transactional
    public InviteData.RedeemedInvite redeem(String token, @Nullable String publicName) {
        if (StringUtils.isBlank(token)) {
            return InviteData.RedeemedInvite.error(REDEMPTION_REFUSED);
        }

        final Invite invite = inviteRepository.findByTokenForUpdate(token);
        final long now = System.currentTimeMillis();

        final String refusal = InviteTokenUtils.redemptionRefusalReason(invite, now);
        if (refusal!=null || invite==null) {
            // logged for the operator, never returned to the caller
            log.warn("01.238.060 invite redemption refused, reason: {}", refusal);
            return InviteData.RedeemedInvite.error(REDEMPTION_REFUSED);
        }

        final String username = InviteTokenUtils.newUsername();
        final String rawPassword = InviteTokenUtils.newPassword();

        AccountData.NewAccount newAccount = new AccountData.NewAccount();
        newAccount.username = username;
        newAccount.password = rawPassword;
        newAccount.password2 = rawPassword;
        newAccount.publicName = StringUtils.isBlank(publicName)
                ? (invite.description==null ? username : invite.description)
                : publicName;

        OperationStatusRest status = accountTxService.addAccount(newAccount, invite.companyId, invite.roles);
        if (status.status!=EnumsApi.OperationStatus.OK) {
            log.error("01.238.080 account creation failed during invite redemption: {}", status.getErrorMessagesAsStr());
            return InviteData.RedeemedInvite.error(REDEMPTION_REFUSED);
        }

        final Account account = accountRepository.findByUsername(username);
        if (account==null) {
            log.error("01.238.100 account was reported created but not found, username: {}", username);
            return InviteData.RedeemedInvite.error(REDEMPTION_REFUSED);
        }

        // Marking the invite spent in the SAME transaction as account creation is
        // what makes single-use real: a crash between the two would otherwise
        // leave a live token that had already minted an account.
        invite.invitedAccountId = account.id;
        invite.redeemedOn = now;
        inviteRepository.save(invite);

        return new InviteData.RedeemedInvite(
                true, null, username, rawPassword, account.id, invite.companyId);
    }

    /** Withdraw an unredeemed invite. Redeemed rows are kept for audit. */
    @Transactional
    public OperationStatusRest withdraw(Long inviteId, Long companyUniqueId) {
        Invite invite = inviteRepository.findById(inviteId).orElse(null);
        if (invite==null || !invite.companyId.equals(companyUniqueId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "01.238.120 Invite wasn't found, inviteId: " + inviteId);
        }
        invite.deleted = true;
        inviteRepository.save(invite);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }
}

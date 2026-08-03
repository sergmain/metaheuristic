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

import java.util.Objects;

/**
 * Creation and redemption of single-use {@link Invite} tokens.
 *
 * <p>An invite sets the password of an EXISTING account. It never creates one:
 * the account, its username and its roles are established beforehand through
 * the ordinary account machinery, and this service only hands possession of it
 * to the intended holder. Purpose-agnostic — nothing here knows why the account
 * exists.
 *
 * <p><b>Redemption discloses no reason.</b> {@link InviteTokenUtils} tells apart
 * notFound / withdrawn / alreadyRedeemed / expired, and this service logs the
 * distinction for the operator — but every refusal returns the same opaque
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

    /**
     * Issue a token that will set the password of {@code accountId}.
     *
     * <p>The account must already belong to {@code companyUniqueId}. Checking it
     * here rather than trusting the caller is what stops an admin of one company
     * issuing a token against another company's account — the request names an
     * account id, and an id alone carries no tenancy.
     */
    @Transactional
    public InviteData.CreatedInvite createInvite(
            Long companyUniqueId, Long accountId, Long createdByAccountId,
            @Nullable String description, long ttlMillis) {

        if (ttlMillis <= 0) {
            return InviteData.CreatedInvite.error("01.238.040 ttlMillis must be positive, actual: " + ttlMillis);
        }

        final Account account = accountRepository.findById(accountId).orElse(null);
        if (account==null || !Objects.equals(account.companyId, companyUniqueId)) {
            return InviteData.CreatedInvite.error("01.238.020 Account wasn't found, accountId: " + accountId);
        }

        final long now = System.currentTimeMillis();

        Invite invite = new Invite();
        invite.companyId = companyUniqueId;
        invite.accountId = accountId;
        invite.token = InviteTokenUtils.newToken();
        invite.description = description;
        invite.createdOn = now;
        invite.expiredOn = now + ttlMillis;
        invite.createdByAccountId = createdByAccountId;
        invite.deleted = false;

        inviteRepository.save(invite);

        return new InviteData.CreatedInvite(invite.id, invite.token, invite.expiredOn);
    }

    /**
     * Redeem a token, setting a freshly generated password on the invite's
     * account and returning the credential once.
     *
     * <p>The password is GENERATED rather than chosen by the redeemer: this
     * credential is used by a machine over HTTP Basic, so there is no human
     * memorability to trade against entropy, and letting an unauthenticated
     * caller choose it would put the account's strength in the hands of whoever
     * happened to hold the token.
     */
    @Transactional
    public InviteData.RedeemedInvite redeem(String token) {
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

        final Account account = accountRepository.findById(invite.accountId).orElse(null);
        if (account==null) {
            log.error("01.238.100 invite names an account that no longer exists, accountId: {}", invite.accountId);
            return InviteData.RedeemedInvite.error(REDEMPTION_REFUSED);
        }

        final String rawPassword = InviteTokenUtils.newPassword();
        final OperationStatusRest status = accountTxService.passwordEditFormCommit(
                account.id, rawPassword, rawPassword, invite.companyId);
        if (status.status!=EnumsApi.OperationStatus.OK) {
            log.error("01.238.080 password could not be set during invite redemption: {}",
                    status.getErrorMessagesAsStr());
            return InviteData.RedeemedInvite.error(REDEMPTION_REFUSED);
        }

        // Marking the invite spent in the SAME transaction as the password write
        // is what makes single-use real: a crash between the two would otherwise
        // leave a live token whose password had already been handed out.
        invite.redeemedOn = now;
        inviteRepository.save(invite);

        return new InviteData.RedeemedInvite(
                account.getUsername(), rawPassword, account.id, invite.companyId);
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

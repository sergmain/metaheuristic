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

import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.beans.AccountRevision;
import ai.metaheuristic.ai.dispatcher.repositories.AccountRepository;
import ai.metaheuristic.ai.dispatcher.repositories.AccountRevisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write-side surface for the envelope/satellite Account split.
 *
 * The split, restated for callers:
 *  - Envelope (MH_ACCOUNT) carries identity (USERNAME, COMPANY_ID, CREATED_ON)
 *    and Spring-Security primitives (PASSWORD, IS_ENABLED, ROLES, the three
 *    expired/locked flags). Updates to envelope-resident fields are plain
 *    UPDATEs — see {@link #updatePassword}, {@link #updateEnabled},
 *    {@link #updateRoles}.
 *  - Satellite (MH_ACCOUNT_REVISION) carries profile/audit scalars
 *    (PUBLIC_NAME, MAIL_ADDRESS, PHONE, PHONE_AS_STR, UPDATED_ON, SECRET_KEY,
 *    TWO_FA, PARAMS) and IS_DELETED. Updates to those fields are INSERTs of a
 *    new revision row plus an envelope UPDATE to repoint HEAD_REVISION_ID.
 *
 * Extended security auditing (revisioned PASSWORD/IS_ENABLED/ROLES history)
 * is a separate later task — by intention this writer does not yet snapshot
 * those changes into the satellite.
 *
 * Contracts enforced on the satellite:
 *  - Each new revision row has REVISION = (max revision for this account) + 1, starting at 1.
 *  - Once a revision with IS_DELETED=true exists for an account, no further revisions are allowed.
 *  - A tombstone revision flips the envelope's IS_DELETED to true in the same transaction.
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class AccountRevisionWriter {

    private final AccountRepository accountRepository;
    private final AccountRevisionRepository accountRevisionRepository;

    /** Payload for {@link #create} and {@link #writeNewRevision}. */
    public record ProfilePayload(
            String publicName,
            @Nullable String mailAddress,
            @Nullable String phone,
            @Nullable String phoneAsStr,
            @Nullable String secretKey,
            boolean twoFA,
            @Nullable String params
    ) {}

    /**
     * Create a brand-new Account: envelope INSERT + first revision INSERT
     * (REVISION=1) + envelope UPDATE to point HEAD_REVISION_ID at the new
     * revision.
     *
     * @param envelopeDraft an unsaved Account carrying identity + security primitives.
     *                      id and headRevisionId must be null.
     * @param profile current profile/audit scalars going into the first satellite row.
     * @return the freshly persisted envelope, now with id and headRevisionId set
     */
    @Transactional
    public Account create(Account envelopeDraft, ProfilePayload profile) {
        if (envelopeDraft.id != null) {
            throw new IllegalStateException("create() expects an unsaved envelope; got id=" + envelopeDraft.id);
        }
        envelopeDraft.deleted = false;
        envelopeDraft.headRevisionId = null;
        Account envelope = accountRepository.save(envelopeDraft);

        long now = System.currentTimeMillis();
        AccountRevision rev = new AccountRevision();
        rev.accountId = envelope.id;
        rev.revision = 1L;
        rev.publicName = profile.publicName();
        rev.mailAddress = profile.mailAddress();
        rev.phone = profile.phone();
        rev.phoneAsStr = profile.phoneAsStr();
        rev.updatedOn = now;
        rev.secretKey = profile.secretKey();
        rev.twoFA = profile.twoFA();
        rev.setParams(profile.params());
        rev.deleted = false;
        rev.createdOn = now;
        rev = accountRevisionRepository.save(rev);

        envelope.headRevisionId = rev.id;
        envelope = accountRepository.save(envelope);
        log.info("Created Account id={}, username={}, headRevisionId={}", envelope.id, envelope.username, rev.id);
        return envelope;
    }

    /**
     * Record a new profile revision for an existing Account. INSERT into the
     * satellite with REVISION = max+1, then UPDATE the envelope's
     * HEAD_REVISION_ID.
     *
     * @throws IllegalStateException if the latest existing revision is a tombstone.
     */
    @Transactional
    public AccountRevision writeNewRevision(Long accountId, ProfilePayload profile) {
        Account envelope = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Account.id=" + accountId + " not found"));
        assertNotTombstoned(envelope);

        long nextRevision = nextRevisionNumber(accountId);
        long now = System.currentTimeMillis();

        AccountRevision rev = new AccountRevision();
        rev.accountId = envelope.id;
        rev.revision = nextRevision;
        rev.publicName = profile.publicName();
        rev.mailAddress = profile.mailAddress();
        rev.phone = profile.phone();
        rev.phoneAsStr = profile.phoneAsStr();
        rev.updatedOn = now;
        rev.secretKey = profile.secretKey();
        rev.twoFA = profile.twoFA();
        rev.setParams(profile.params());
        rev.deleted = false;
        rev.createdOn = now;
        rev = accountRevisionRepository.save(rev);

        envelope.headRevisionId = rev.id;
        accountRepository.save(envelope);
        log.info("New AccountRevision id={}, accountId={}, revision={}", rev.id, accountId, nextRevision);
        return rev;
    }

    /**
     * Update PASSWORD on the envelope. Envelope-only write — no satellite row.
     * Extended security-audit history will be added later as a separate task.
     */
    @Transactional
    public void updatePassword(Long accountId, String newPassword) {
        Account envelope = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Account.id=" + accountId + " not found"));
        assertNotTombstoned(envelope);
        envelope.password = newPassword;
        accountRepository.save(envelope);
    }

    /** Update IS_ENABLED on the envelope. Envelope-only write. */
    @Transactional
    public void updateEnabled(Long accountId, boolean enabled) {
        Account envelope = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Account.id=" + accountId + " not found"));
        assertNotTombstoned(envelope);
        envelope.enabled = enabled;
        accountRepository.save(envelope);
    }

    /** Update ROLES on the envelope. Envelope-only write. */
    @Transactional
    public void updateRoles(Long accountId, @Nullable String roles) {
        Account envelope = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Account.id=" + accountId + " not found"));
        assertNotTombstoned(envelope);
        envelope.roles = roles;
        accountRepository.save(envelope);
    }

    /**
     * Soft-delete the Account: INSERT a tombstone satellite revision carrying
     * the current head's profile fields for traceability, then UPDATE the
     * envelope's HEAD_REVISION_ID and IS_DELETED=true.
     */
    @Transactional
    public AccountRevision softDelete(Long accountId) {
        Account envelope = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Account.id=" + accountId + " not found"));
        assertNotTombstoned(envelope);

        AccountRevision currentHead = envelope.headRevisionId == null
                ? null
                : accountRevisionRepository.findById(envelope.headRevisionId).orElse(null);

        long nextRevision = nextRevisionNumber(accountId);
        long now = System.currentTimeMillis();

        AccountRevision tombstone = new AccountRevision();
        tombstone.accountId = envelope.id;
        tombstone.revision = nextRevision;
        tombstone.publicName = currentHead != null ? currentHead.publicName : "";
        tombstone.mailAddress = currentHead != null ? currentHead.mailAddress : null;
        tombstone.phone = currentHead != null ? currentHead.phone : null;
        tombstone.phoneAsStr = currentHead != null ? currentHead.phoneAsStr : null;
        tombstone.updatedOn = now;
        tombstone.secretKey = currentHead != null ? currentHead.secretKey : null;
        tombstone.twoFA = currentHead != null && currentHead.twoFA;
        tombstone.setParams(currentHead != null ? currentHead.getParams() : null);
        tombstone.deleted = true;
        tombstone.createdOn = now;
        tombstone = accountRevisionRepository.save(tombstone);

        envelope.headRevisionId = tombstone.id;
        envelope.deleted = true;
        accountRepository.save(envelope);
        log.info("Tombstoned Account id={}, tombstoneRevisionId={}, revision={}", accountId, tombstone.id, nextRevision);
        return tombstone;
    }

    private long nextRevisionNumber(Long accountId) {
        Long maxRev = accountRevisionRepository.findMaxRevision(accountId);
        return maxRev == null ? 1L : maxRev + 1L;
    }

    private static void assertNotTombstoned(Account envelope) {
        if (envelope.deleted) {
            throw new IllegalStateException(
                    "Account.id=" + envelope.id + " is tombstoned; no further revisions allowed");
        }
    }
}

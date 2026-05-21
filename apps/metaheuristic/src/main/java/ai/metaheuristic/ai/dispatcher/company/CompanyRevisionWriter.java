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

package ai.metaheuristic.ai.dispatcher.company;

import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.ai.dispatcher.beans.CompanyRevision;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRepository;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRevisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write-side surface for the envelope/satellite Company split. Replaces direct
 * use of {@code CompanyCache.save(company)} at every call site that mutates
 * Company state (NAME, PARAMS, IS_DELETED).
 *
 * All writes here are INSERTs into MH_COMPANY_REVISION (the satellite). The
 * only UPDATE issued is on the envelope's HEAD_REVISION_ID (and IS_DELETED
 * mirror on tombstone), which is metadata about the audit chain, not the
 * audited data itself.
 *
 * Contracts enforced:
 *  - Each new revision row has REVISION = (max revision for this company) + 1, starting at 1.
 *  - Once a revision with IS_DELETED=true exists for a company, no further revisions are allowed.
 *  - A tombstone revision flips the envelope's IS_DELETED to true in the same transaction.
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class CompanyRevisionWriter {

    private final CompanyRepository companyRepository;
    private final CompanyRevisionRepository companyRevisionRepository;
    private final CompanyCache companyCache;

    /**
     * Create a brand-new Company: envelope INSERT + first revision INSERT
     * (REVISION=1) + envelope UPDATE to point HEAD_REVISION_ID at the new
     * revision. All three operations in one transaction.
     *
     * @param uniqueId business key (replication identity from the asset server)
     * @param name current Company.NAME
     * @param params current Company.PARAMS (may be null/empty)
     * @return the freshly persisted envelope, now with id and headRevisionId set
     */
    @Transactional
    public Company create(Long uniqueId, String name, @Nullable String params) {
        Company envelope = new Company();
        envelope.uniqueId = uniqueId;
        envelope.deleted = false;
        envelope.headRevisionId = null;
        envelope = companyCache.save(envelope);

        CompanyRevision rev = new CompanyRevision();
        rev.companyId = envelope.id;
        rev.revision = 1L;
        rev.name = name;
        rev.setParams(params);
        rev.deleted = false;
        rev.createdOn = System.currentTimeMillis();
        rev = companyRevisionRepository.save(rev);

        envelope.headRevisionId = rev.id;
        envelope = companyCache.save(envelope);
        log.info("Created Company id={}, uniqueId={}, headRevisionId={}", envelope.id, envelope.uniqueId, rev.id);
        return envelope;
    }

    /**
     * Record a new revision for an existing Company. INSERT into the satellite
     * with REVISION = max+1, then UPDATE the envelope's HEAD_REVISION_ID.
     *
     * @throws IllegalStateException if the latest existing revision is a tombstone (IS_DELETED=true)
     */
    @Transactional
    public CompanyRevision writeNewRevision(Long companyId, String name, @Nullable String params) {
        Company envelope = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalStateException("Company.id=" + companyId + " not found"));
        assertNotTombstoned(envelope);

        long nextRevision = nextRevisionNumber(companyId);

        CompanyRevision rev = new CompanyRevision();
        rev.companyId = envelope.id;
        rev.revision = nextRevision;
        rev.name = name;
        rev.setParams(params);
        rev.deleted = false;
        rev.createdOn = System.currentTimeMillis();
        rev = companyRevisionRepository.save(rev);

        envelope.headRevisionId = rev.id;
        companyCache.save(envelope);
        log.info("New CompanyRevision id={}, companyId={}, revision={}", rev.id, companyId, nextRevision);
        return rev;
    }

    /**
     * Soft-delete the Company: INSERT a tombstone revision (IS_DELETED=true)
     * carrying the current head's NAME/PARAMS for traceability, then UPDATE the
     * envelope's HEAD_REVISION_ID and IS_DELETED=true.
     *
     * @throws IllegalStateException if the Company is already tombstoned.
     */
    @Transactional
    public CompanyRevision softDelete(Long companyId) {
        Company envelope = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalStateException("Company.id=" + companyId + " not found"));
        assertNotTombstoned(envelope);

        CompanyRevision currentHead = envelope.headRevisionId == null
                ? null
                : companyRevisionRepository.findById(envelope.headRevisionId).orElse(null);
        String name = currentHead != null ? currentHead.name : "";
        String params = currentHead != null ? currentHead.getParams() : null;

        long nextRevision = nextRevisionNumber(companyId);

        CompanyRevision tombstone = new CompanyRevision();
        tombstone.companyId = envelope.id;
        tombstone.revision = nextRevision;
        tombstone.name = name;
        tombstone.setParams(params);
        tombstone.deleted = true;
        tombstone.createdOn = System.currentTimeMillis();
        tombstone = companyRevisionRepository.save(tombstone);

        envelope.headRevisionId = tombstone.id;
        envelope.deleted = true;
        companyCache.save(envelope);
        log.info("Tombstoned Company id={}, tombstoneRevisionId={}, revision={}", companyId, tombstone.id, nextRevision);
        return tombstone;
    }

    private long nextRevisionNumber(Long companyId) {
        Long maxRev = companyRevisionRepository.findMaxRevision(companyId);
        return maxRev == null ? 1L : maxRev + 1L;
    }

    private static void assertNotTombstoned(Company envelope) {
        if (envelope.deleted) {
            throw new IllegalStateException(
                    "Company.id=" + envelope.id + " is tombstoned; no further revisions allowed");
        }
    }
}

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
import ai.metaheuristic.ai.dispatcher.data.CompanyData;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRepository;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRevisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Read-side composer for the envelope/satellite Company split.
 *
 * Always go through {@link #getCurrent(Long)} or {@link #getCurrentByUniqueId(Long)}
 * when code needs the current NAME/PARAMS of a Company. Loading a {@link Company}
 * by id no longer exposes NAME/PARAMS directly — those fields moved to
 * {@link CompanyRevision} (the satellite) and are looked up here by HEAD_REVISION_ID.
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanyRevisionRepository companyRevisionRepository;

    /** Envelope + head revision joined into a CompanyWithRevision. Returns null if envelope not found. */
    public CompanyData.@Nullable CompanyWithRevision getCurrent(Long companyId) {
        Company envelope = companyRepository.findById(companyId).orElse(null);
        if (envelope == null) {
            return null;
        }
        return composeFromEnvelope(envelope);
    }

    /** Envelope + head revision joined into a CompanyWithRevision, found by UNIQUE_ID. Returns null if envelope not found. */
    public CompanyData.@Nullable CompanyWithRevision getCurrentByUniqueId(Long uniqueId) {
        Company envelope = companyRepository.findByUniqueId(uniqueId);
        if (envelope == null) {
            return null;
        }
        return composeFromEnvelope(envelope);
    }

    private CompanyData.@Nullable CompanyWithRevision composeFromEnvelope(Company envelope) {
        if (envelope.headRevisionId == null) {
            // envelope without a head revision is a corrupt state — bootstrap should have written rev=1
            log.error("Company.id={} has no HEAD_REVISION_ID; envelope is missing its satellite", envelope.id);
            return null;
        }
        CompanyRevision head = companyRevisionRepository.findById(envelope.headRevisionId).orElse(null);
        if (head == null) {
            log.error("Company.id={} HEAD_REVISION_ID={} points at a missing CompanyRevision row",
                    envelope.id, envelope.headRevisionId);
            return null;
        }
        return new CompanyData.CompanyWithRevision(
                envelope.id,
                envelope.uniqueId,
                envelope.deleted,
                envelope.headRevisionId,
                head.name,
                head.getParams(),
                head.getCompanyParamsYaml()
        );
    }
}

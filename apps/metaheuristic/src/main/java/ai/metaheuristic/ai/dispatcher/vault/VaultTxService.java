/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.vault;

import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.ai.dispatcher.beans.CompanyRevision;
import ai.metaheuristic.ai.dispatcher.company.CompanyCache;
import ai.metaheuristic.ai.dispatcher.company.CompanyRevisionWriter;
import ai.metaheuristic.ai.dispatcher.event.events.VaultEntryChangedTxEvent;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRepository;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRevisionRepository;
import ai.metaheuristic.ai.yaml.company.CompanyParamsYaml;
import ai.metaheuristic.ai.yaml.company.CompanyParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional companion for {@link VaultService}.
 *
 * <p>Loads the Company under a pessimistic lock, applies a per-company
 * {@link CompanyParamsYaml.VaultEntries} mutation, and persists the change
 * as a new {@link CompanyRevision} via {@link CompanyRevisionWriter}. The
 * {@link VaultEntryChangedTxEvent} is published inside the transaction;
 * {@code EventsBoundedToTx} converts it to a plain {@code VaultEntryChangedEvent}
 * after commit so fan-out runs only on successful persistence.
 *
 * @author Sergio Lissner
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class VaultTxService {

    private final CompanyRepository companyRepository;
    private final CompanyRevisionRepository companyRevisionRepository;
    private final CompanyCache companyCache;
    private final CompanyRevisionWriter companyRevisionWriter;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Replace the company's encrypted vault blob and persist.
     *
     * <p>{@code keyCode} + {@code action} are only used for the change-event
     * payload (cache-invalidation fan-out). The vault entry itself is opaque
     * to this method — encryption/serialization happens in {@link VaultService}.
     *
     * @return true on success, false if the company was not found
     */
    @Transactional
    public boolean saveVaultBlob(long companyUniqueId, CompanyParamsYaml.VaultEntries blob,
                                 String keyCode, String action) {
        Company c = companyRepository.findByUniqueIdForUpdate(companyUniqueId);
        if (c == null) {
            log.warn("0670.010 Company not found for vault save, companyUniqueId={}", companyUniqueId);
            return false;
        }
        // Pull current head revision to derive a base CompanyParamsYaml + NAME to carry into the new revision.
        CompanyRevision currentHead = c.headRevisionId == null
                ? null
                : companyRevisionRepository.findById(c.headRevisionId).orElse(null);
        CompanyParamsYaml cpy = currentHead != null ? currentHead.getCompanyParamsYaml() : new CompanyParamsYaml();
        cpy.vault = blob;
        long now = System.currentTimeMillis();
        if (cpy.createdOn == 0L) {
            cpy.createdOn = now;
        }
        cpy.updatedOn = now;
        String paramsYaml = CompanyParamsYamlUtils.BASE_YAML_UTILS.toString(cpy);
        String name = currentHead != null ? currentHead.name : "";
        companyRevisionWriter.writeNewRevision(c.id, name, paramsYaml);
        eventPublisher.publishEvent(new VaultEntryChangedTxEvent(companyUniqueId, keyCode, action));
        return true;
    }

    /**
     * Read the current vault blob for the given company.
     * Returns null if the company doesn't exist or has no vault yet.
     * Read-only transaction so Hibernate doesn't dirty-check on the way out.
     */
    @Transactional(readOnly = true)
    public CompanyParamsYaml.@Nullable VaultEntries loadVaultBlob(long companyUniqueId) {
        Company c = companyCache.findByUniqueId(companyUniqueId);
        if (c == null) {
            return null;
        }
        // Vault blob lives inside PARAMS on the head CompanyRevision.
        CompanyRevision head = c.headRevisionId == null
                ? null
                : companyRevisionRepository.findById(c.headRevisionId).orElse(null);
        if (head == null) {
            return null;
        }
        return head.getCompanyParamsYaml().vault;
    }
}


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

package ai.metaheuristic.ai.dispatcher.replication;

import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.ai.dispatcher.company.CompanyCache;
import ai.metaheuristic.ai.dispatcher.company.CompanyRevisionWriter;
import ai.metaheuristic.ai.dispatcher.data.ReplicationData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static ai.metaheuristic.ai.dispatcher.replication.ReplicationCompanyTopLevelService.CompanyLoopEntry;

/**
 * @author Serge
 * Date: 1/13/2020
 * Time: 7:10 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ReplicationCompanyService {

    private final CompanyCache companyCache;
    private final CompanyRevisionWriter companyRevisionWriter;

    @Transactional
    public void createCompany(ReplicationData.CompanyAsset companyAsset) {
        Company c = companyCache.findByUniqueId(companyAsset.company.uniqueId);
        if (c != null) {
            return;
        }
        if (companyAsset.headRevision == null) {
            log.error("Asset for uniqueId={} is missing headRevision; cannot replicate", companyAsset.company.uniqueId);
            return;
        }

        // Envelope + first revision constructed through the writer so the satellite is properly populated.
        companyRevisionWriter.create(
                companyAsset.company.uniqueId,
                companyAsset.headRevision.name,
                companyAsset.headRevision.getParams()
        );
    }

    @Transactional
    public void updateCompany(CompanyLoopEntry companyLoopEntry, ReplicationData.CompanyAsset companyAsset) {
        if (companyAsset.headRevision == null) {
            log.error("Asset for uniqueId={} is missing headRevision; cannot update",
                    companyLoopEntry.company.uniqueId);
            return;
        }
        // INSERT a new satellite row carrying the upstream NAME/PARAMS; envelope.HEAD_REVISION_ID is repointed.
        companyRevisionWriter.writeNewRevision(
                companyLoopEntry.company.id,
                companyAsset.headRevision.name,
                companyAsset.headRevision.getParams()
        );
    }
}

/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

    @Transactional
    public void createCompany(ReplicationData.CompanyAsset companyAsset) {
        Company c = companyCache.findByUniqueId(companyAsset.company.uniqueId);
        if (c!=null) {
            return;
        }

        //noinspection ConstantConditions
        companyAsset.company.id=null;
        //noinspection ConstantConditions
        companyAsset.company.version=null;
        companyCache.save(companyAsset.company);
    }

    @Transactional
    public void updateCompany(CompanyLoopEntry companyLoopEntry, ReplicationData.CompanyAsset companyAsset) {
        companyLoopEntry.company.name = companyAsset.company.name;
        companyLoopEntry.company.setParams( companyAsset.company.getParams() );

        companyCache.save(companyLoopEntry.company);
    }
}
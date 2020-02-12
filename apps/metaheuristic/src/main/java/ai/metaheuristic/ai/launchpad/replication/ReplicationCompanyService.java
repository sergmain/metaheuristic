/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.replication;

import ai.metaheuristic.ai.launchpad.beans.Company;
import ai.metaheuristic.ai.launchpad.company.CompanyCache;
import ai.metaheuristic.ai.launchpad.data.ReplicationData;
import ai.metaheuristic.ai.launchpad.repositories.CompanyRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Serge
 * Date: 1/13/2020
 * Time: 7:10 PM
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Profile("launchpad")
public class ReplicationCompanyService {

    public final ReplicationCoreService replicationCoreService;
    public final CompanyRepository companyRepository;
    public final CompanyCache companyCache;

    @Data
    @AllArgsConstructor
    private static class CompanyLoopEntry {
        public ReplicationData.CompanyShortAsset companyShort;
        public Company company;
    }

    public void syncCompanies(List<ReplicationData.CompanyShortAsset> actualCompanies) {
        List<CompanyLoopEntry> forUpdating = new ArrayList<>(actualCompanies.size());
        LinkedList<ReplicationData.CompanyShortAsset> forCreating = new LinkedList<>(actualCompanies);

        List<Long> ids = companyRepository.findAllUniqueIds();
        for (Long id : ids) {
            Company c = companyCache.findByUniqueId(id);
            if (c==null) {
                continue;
            }

            boolean isDeleted = true;
            for (ReplicationData.CompanyShortAsset actualCompany : actualCompanies) {
                if (actualCompany.uniqueId.equals(c.uniqueId)) {
                    isDeleted = false;
                    if (actualCompany.updateOn != c.getCompanyParamsYaml().updatedOn) {
                        CompanyLoopEntry companyLoopEntry = new CompanyLoopEntry(actualCompany, c);
                        forUpdating.add(companyLoopEntry);
                    }
                    break;
                }
            }

            if (isDeleted) {
                log.warn("!!! Strange situation - company wasn't found, uniqueId: {}", id);
            }
            forCreating.removeIf(companyShortAsset -> companyShortAsset.uniqueId.equals(c.uniqueId));
        }

        forUpdating.parallelStream().forEach(this::updateCompany);
        forCreating.parallelStream().forEach(this::createCompany);
    }

    private void createCompany(ReplicationData.CompanyShortAsset companyShortAsset) {
        ReplicationData.CompanyAsset companyAsset = getCompanyAsset(companyShortAsset.uniqueId);
        if (companyAsset == null) {
            return;
        }

        Company c = companyRepository.findByUniqueId(companyShortAsset.uniqueId);
        if (c!=null) {
            return;
        }

        companyAsset.company.id=null;
        companyCache.save(companyAsset.company);
    }

    private void updateCompany(CompanyLoopEntry companyLoopEntry) {
        ReplicationData.CompanyAsset companyAsset = getCompanyAsset(companyLoopEntry.company.uniqueId);
        if (companyAsset == null) {
            return;
        }

        companyLoopEntry.company.name = companyAsset.company.name;
        companyLoopEntry.company.setParams( companyAsset.company.getParams() );

        companyCache.save(companyLoopEntry.company);

    }

    private ReplicationData.CompanyAsset getCompanyAsset(Long uniqueId) {
        ReplicationData.CompanyAsset companyAsset = requestCompanyAsset(uniqueId);
        if (companyAsset.isErrorMessages()) {
            log.error("#308.020 Error while getting company with uniqueId "+ uniqueId +", error: " + companyAsset.getErrorMessagesAsStr());
            return null;
        }
        return companyAsset;
    }

    private ReplicationData.CompanyAsset requestCompanyAsset(Long uniqueId) {
        Object data = replicationCoreService.getData(
                "/rest/v1/replication/company", ReplicationData.CompanyAsset.class,
                (uri) -> Request.Post(uri)
                        .bodyForm(Form.form().add("uniqueId", uniqueId.toString()).build(), StandardCharsets.UTF_8)
                        .connectTimeout(5000)
                        .socketTimeout(20000)
        );
        if (data instanceof ReplicationData.AssetAcquiringError) {
            return new ReplicationData.CompanyAsset(((ReplicationData.AssetAcquiringError) data).errorMessages);
        }
        ReplicationData.CompanyAsset response = (ReplicationData.CompanyAsset) data;
        return response;
    }
}
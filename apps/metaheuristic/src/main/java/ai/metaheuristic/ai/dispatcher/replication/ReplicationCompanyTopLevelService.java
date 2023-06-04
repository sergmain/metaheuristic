/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRepository;
import ai.metaheuristic.ai.yaml.company.CompanyParamsYaml;
import ai.metaheuristic.ai.yaml.company.CompanyParamsYamlUtils;
import ai.metaheuristic.commons.S;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * @author Serge
 * Date: 11/24/2020
 * Time: 7:28 PM
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Profile("dispatcher")
public class ReplicationCompanyTopLevelService {

    public final ReplicationCoreService replicationCoreService;
    public final ReplicationCompanyService replicationCompanyService;
    public final CompanyRepository companyRepository;
    public final CompanyCache companyCache;

    @Data
    @AllArgsConstructor
    public static class CompanyLoopEntry {
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
                    CompanyParamsYaml cpy = S.b(c.params) ? new CompanyParamsYaml() : CompanyParamsYamlUtils.BASE_YAML_UTILS.to(c.params);
                    if (actualCompany.updateOn != cpy.updatedOn) {
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

        for (CompanyLoopEntry companyLoopEntry : forUpdating) {
            ReplicationData.CompanyAsset companyAsset = getCompanyAsset(companyLoopEntry.companyShort.uniqueId);
            if (companyAsset == null) {
                return;
            }

            replicationCompanyService.updateCompany(companyLoopEntry, companyAsset);
        }
        forCreating.stream()
                .map(o-> getCompanyAsset(o.uniqueId))
                .filter(Objects::nonNull)
                .forEach(replicationCompanyService::createCompany);

    }

    @Nullable
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
                "/rest/v1/replication/company", ReplicationData.CompanyAsset.class, List.of(new BasicNameValuePair("uniqueId", uniqueId.toString())),
                (uri) -> Request.get(uri).connectTimeout(Timeout.ofSeconds(5))
//                        .socketTimeout(20000)
        );
        if (data instanceof ReplicationData.AssetAcquiringError) {
            return new ReplicationData.CompanyAsset(((ReplicationData.AssetAcquiringError) data).getErrorMessagesAsList());
        }
        ReplicationData.CompanyAsset response = (ReplicationData.CompanyAsset) data;
        return response;
    }
}

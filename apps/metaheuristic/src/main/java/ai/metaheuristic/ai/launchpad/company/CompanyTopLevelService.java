/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.company;

import ai.metaheuristic.ai.launchpad.beans.Company;
import ai.metaheuristic.ai.launchpad.data.CompanyData;
import ai.metaheuristic.ai.launchpad.repositories.CompanyRepository;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Profile("launchpad")
@Service
@RequiredArgsConstructor
public class CompanyTopLevelService {

    public static final int ROWS_IN_TABLE = 50;

    private final CompanyRepository companyRepository;
    private final CompanyCache companyCache;

    public CompanyData.CompaniesResult getCompanies(Pageable pageable)  {
        pageable = ControllerUtils.fixPageSize(ROWS_IN_TABLE, pageable);
        CompanyData.CompaniesResult result = new CompanyData.CompaniesResult();
        result.companies = companyRepository.findAll(pageable);
        return result;
    }

    public OperationStatusRest addCompany(Company company) {
        if (S.b(company.name)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#239.010 Name of company name must not be null");
        }

        companyCache.save(company);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public CompanyData.CompanyResult getCompany(Long companyId){
        Company company = companyRepository.findById(companyId).orElse(null);
        if (company == null) {
            return new CompanyData.CompanyResult("#237.050 company wasn't found, companyId: " + companyId);
        }
        company.setName(null);
        return new CompanyData.CompanyResult(company);
    }

    public OperationStatusRest editFormCommit(Long companyId, String name) {
        Company c = companyRepository.findByIdForUpdate(companyId);
        if (c == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#237.060 company wasn't found, accountId: " + companyId);
        }
        c.setName(name);
        companyCache.save(c);
        return new OperationStatusRest(EnumsApi.OperationStatus.OK,"The data of company was changed successfully", null);
    }
}

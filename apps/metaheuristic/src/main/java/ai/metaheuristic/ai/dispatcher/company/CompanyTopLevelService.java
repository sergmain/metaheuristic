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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.ai.dispatcher.beans.CompanyRevision;
import ai.metaheuristic.ai.dispatcher.beans.Ids;
import ai.metaheuristic.ai.dispatcher.data.CompanyData;
import ai.metaheuristic.ai.dispatcher.data.SimpleCompany;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRepository;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRevisionRepository;
import ai.metaheuristic.ai.dispatcher.repositories.IdsRepository;
import ai.metaheuristic.ai.yaml.company.CompanyParamsYaml;
import ai.metaheuristic.ai.yaml.company.CompanyParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class CompanyTopLevelService {

    public static final int ROWS_IN_TABLE = 50;

    private final Globals globals;
    private final CompanyRepository companyRepository;
    private final CompanyRevisionRepository companyRevisionRepository;
    private final CompanyCache companyCache;
    private final CompanyRevisionWriter companyRevisionWriter;
    private final IdsRepository idsRepository;

    public CompanyData.SimpleCompaniesResult getCompanies(Pageable pageable) {
        pageable = PageUtils.fixPageSize(ROWS_IN_TABLE, pageable);
        CompanyData.SimpleCompaniesResult result = new CompanyData.SimpleCompaniesResult();
        result.companies = companyRepository.findAllAsSimple(pageable);
        result.assetMode = globals.dispatcher.asset.mode;
        return result;
    }

    @Transactional
    public OperationStatusRest addCompany(String companyName) {
        if (globals.dispatcher.asset.mode== EnumsApi.DispatcherAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "237.010 Can't create a new company while 'replicated' mode of asset is active");
        }
        if (S.b(companyName)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "237.020 Name of company name must not be null");
        }

        CompanyParamsYaml cpy = new CompanyParamsYaml();
        cpy.createdOn = System.currentTimeMillis();
        cpy.updatedOn = cpy.createdOn;
        String paramsYaml = CompanyParamsYamlUtils.BASE_YAML_UTILS.toString(cpy);

        Long uniqueId = getUniqueId();
        companyRevisionWriter.create(uniqueId, companyName, paramsYaml);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public Long getUniqueId() {
        Long maxUniqueId = companyRepository.getMaxUniqueIdValue();
        if (maxUniqueId==null) {
            // 2L because 1 is reserved for 'main company'
            maxUniqueId = 2L;
        }
        int compare;
        Long newUniqueId;
        do {
            newUniqueId = idsRepository.save(new Ids()).id;
            idsRepository.deleteById(newUniqueId);
            compare = Long.compare(newUniqueId, maxUniqueId);
        } while(compare<1);
        return newUniqueId;
    }

    @Transactional(readOnly = true)
    public CompanyData.SimpleCompanyResult getSimpleCompany(Long companyUniqueId){
        Company company = companyCache.findByUniqueId(companyUniqueId);
        if (company == null) {
            return new CompanyData.SimpleCompanyResult("237.050 company wasn't found, companyUniqueId: " + companyUniqueId);
        }
        // Head NAME/PARAMS live on the satellite — load via HEAD_REVISION_ID.
        CompanyRevision head = company.headRevisionId == null
                ? null
                : companyRevisionRepository.findById(company.headRevisionId).orElse(null);
        if (head == null) {
            return new CompanyData.SimpleCompanyResult("237.051 company has no head revision, companyUniqueId: " + companyUniqueId);
        }
        String groups = "";
        CompanyParamsYaml cpy = head.getCompanyParamsYaml();

        if (cpy.ac!=null && !S.b(cpy.ac.groups)) {
            groups = cpy.ac.groups;
        }
        SimpleCompany simpleCompany = new SimpleCompany();
        simpleCompany.id = company.id;
        simpleCompany.uniqueId = company.uniqueId;
        simpleCompany.name = head.name;
        CompanyData.SimpleCompanyResult companyResult = new CompanyData.SimpleCompanyResult(simpleCompany);
        companyResult.companyAccessControl.groups = groups;
        return companyResult;
    }

    /**
     *
     * @param companyUniqueId contains a value from Company.uniqueId, !not! from Company.Id
     */
    @Transactional
    public OperationStatusRest editFormCommit(Long companyUniqueId, String name, String groups) {
        if (globals.dispatcher.asset.mode== EnumsApi.DispatcherAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "237.055 Can't edit a company while 'replicated' mode of asset is active");
        }
        Company c = companyRepository.findByUniqueIdForUpdate(companyUniqueId);
        if (c == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"237.060 company wasn't found, companyUniqueId: " + companyUniqueId);
        }

        // Pull the current head revision to derive a base CompanyParamsYaml to mutate.
        CompanyRevision currentHead = c.headRevisionId == null
                ? null
                : companyRevisionRepository.findById(c.headRevisionId).orElse(null);

        Long createdOn = null;
        if (currentHead == null || S.b(currentHead.getParams())) {
            createdOn = System.currentTimeMillis();
        }

        CompanyParamsYaml cpy;
        try {
            cpy = currentHead != null ? currentHead.getCompanyParamsYaml() : new CompanyParamsYaml();
            if (createdOn!=null) {
                cpy.createdOn = createdOn;
            }
            cpy.updatedOn = System.currentTimeMillis();
            cpy.ac = new CompanyParamsYaml.AccessControl(groups);
        } catch (Throwable th) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"237.080 company params is in wrong format, error: " + th.getMessage());
        }
        String paramsYaml = CompanyParamsYamlUtils.BASE_YAML_UTILS.toString(cpy);
        companyRevisionWriter.writeNewRevision(c.id, name, paramsYaml);
        return new OperationStatusRest(EnumsApi.OperationStatus.OK,"The data of company was changed successfully", null);
    }

    @Nullable
    @Transactional(readOnly = true)
    public Company getCompanyByUniqueId(Long uniqueId) {
        return companyCache.findByUniqueId(uniqueId);
    }
}


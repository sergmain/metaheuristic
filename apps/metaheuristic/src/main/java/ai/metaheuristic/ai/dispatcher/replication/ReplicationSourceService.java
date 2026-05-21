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

import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.beans.AccountRevision;
import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.ai.dispatcher.beans.CompanyRevision;
import ai.metaheuristic.ai.dispatcher.data.ReplicationData;
import ai.metaheuristic.ai.dispatcher.repositories.AccountRepository;
import ai.metaheuristic.ai.dispatcher.repositories.AccountRevisionRepository;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRepository;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRevisionRepository;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 1/9/2020
 * Time: 12:16 AM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ReplicationSourceService {

    private final CompanyRepository companyRepository;
    private final CompanyRevisionRepository companyRevisionRepository;
    private final AccountRepository accountRepository;
    private final AccountRevisionRepository accountRevisionRepository;
    private final SourceCodeRepository sourceCodeRepository;
    private final FunctionRepository functionRepository;
    private final ReplicationSourceHelperService replicationSourceHelperService;

    public ReplicationData.AssetStateResponse currentAssets() {
        return replicationSourceHelperService.currentAssets();
    }

    public ReplicationData.FunctionAsset getFunction(String functionCode) {
        ReplicationData.FunctionAsset functionAsset = new ReplicationData.FunctionAsset(functionRepository.findByCode(functionCode));
        return functionAsset;
    }

    public ReplicationData.SourceCodeAsset getSourceCode(String uid) {
        ReplicationData.SourceCodeAsset sourceCodeAsset = new ReplicationData.SourceCodeAsset(sourceCodeRepository.findByUid(uid));
        return sourceCodeAsset;
    }

    public ReplicationData.CompanyAsset getCompany(long uniqueId) {
        Company envelope = companyRepository.findByUniqueId(uniqueId);
        CompanyRevision head = (envelope == null || envelope.headRevisionId == null)
                ? null
                : companyRevisionRepository.findById(envelope.headRevisionId).orElse(null);
        return new ReplicationData.CompanyAsset(envelope, head);
    }

    public ReplicationData.AccountAsset getAccount(String username) {
        Account envelope = accountRepository.findByUsername(username);
        AccountRevision head = (envelope == null || envelope.headRevisionId == null)
                ? null
                : accountRevisionRepository.findById(envelope.headRevisionId).orElse(null);
        return new ReplicationData.AccountAsset(envelope, head);
    }
}


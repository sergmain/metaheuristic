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

package ai.metaheuristic.ai.dispatcher.replication;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.account.AccountCache;
import ai.metaheuristic.ai.dispatcher.company.CompanyCache;
import ai.metaheuristic.ai.dispatcher.data.ReplicationData;
import ai.metaheuristic.ai.dispatcher.repositories.AccountRepository;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRepository;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Serge
 * Date: 1/9/2020
 * Time: 12:16 AM
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Profile("dispatcher")
public class ReplicationSourceService {

    public final Globals globals;
    public final CompanyRepository companyRepository;
    public final AccountRepository accountRepository;
    public final SourceCodeRepository sourceCodeRepository;
    public final FunctionRepository functionRepository;
    public final SourceCodeCache sourceCodeCache;
    public final AccountCache accountCache;
    public final CompanyCache companyCache;
    public final ReplicationSourceHelperService replicationSourceHelperService;

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
        ReplicationData.CompanyAsset companyAsset = new ReplicationData.CompanyAsset(companyRepository.findByUniqueId(uniqueId));
        return companyAsset;
    }

    public ReplicationData.AccountAsset getAccount(String username) {
        ReplicationData.AccountAsset accountAsset = new ReplicationData.AccountAsset(accountRepository.findByUsername(username));
        return accountAsset;
    }
}

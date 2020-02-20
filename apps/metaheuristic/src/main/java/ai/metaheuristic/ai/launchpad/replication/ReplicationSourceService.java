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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.launchpad.account.AccountCache;
import ai.metaheuristic.ai.launchpad.beans.Account;
import ai.metaheuristic.ai.launchpad.beans.Company;
import ai.metaheuristic.ai.launchpad.beans.SourceCodeImpl;
import ai.metaheuristic.ai.launchpad.company.CompanyCache;
import ai.metaheuristic.ai.launchpad.data.ReplicationData;
import ai.metaheuristic.ai.launchpad.repositories.AccountRepository;
import ai.metaheuristic.ai.launchpad.repositories.CompanyRepository;
import ai.metaheuristic.ai.launchpad.repositories.SnippetRepository;
import ai.metaheuristic.ai.launchpad.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.launchpad.source_code.SourceCodeCache;
import ai.metaheuristic.ai.yaml.company.CompanyParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 1/9/2020
 * Time: 12:16 AM
 */
@SuppressWarnings("UnnecessaryLocalVariable")
@Service
@RequiredArgsConstructor
@Slf4j
@Profile("launchpad")
public class ReplicationSourceService {

    public final Globals globals;
    public final CompanyRepository companyRepository;
    public final AccountRepository accountRepository;
    public final SourceCodeRepository sourceCodeRepository;
    public final SnippetRepository snippetRepository;
    public final SourceCodeCache sourceCodeCache;
    public final AccountCache accountCache;
    public final CompanyCache companyCache;

    public ReplicationData.AssetStateResponse currentAssets() {
        ReplicationData.AssetStateResponse res = new ReplicationData.AssetStateResponse();
        res.companies.addAll(companyRepository.findAllUniqueIds().parallelStream()
                .map(id->{
                    Company company = companyCache.findByUniqueId(id);

                    CompanyParamsYaml params = company.getCompanyParamsYaml();
                    return new ReplicationData.CompanyShortAsset(company.uniqueId, params.updatedOn);
                })
                .collect(Collectors.toList()));

        res.usernames.addAll(accountRepository.findAllUsernames().parallelStream()
                .map(username->{
                    Account account = accountCache.findByUsername(username);

                    return new ReplicationData.AccountShortAsset(account.username, account.updatedOn);
                })
                .collect(Collectors.toList()));

        res.snippets.addAll(snippetRepository.findAllSnippetCodes());
        res.sourceCodes.addAll(sourceCodeRepository.findAllAsIds().parallelStream()
                .map(id->{
                    SourceCodeImpl sourceCode = sourceCodeCache.findById(id);
                    SourceCodeStoredParamsYaml params = sourceCode.getSourceCodeStoredParamsYaml();
                    if (params.internalParams.archived) {
                        return null;
                    }
                    return new ReplicationData.SourceCodeShortAsset(sourceCode.uid, params.internalParams.updatedOn);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
        return res;
    }

    public ReplicationData.SnippetAsset getSnippet(String snippetCode) {
        ReplicationData.SnippetAsset snippetAsset = new ReplicationData.SnippetAsset(snippetRepository.findByCode(snippetCode));
        return snippetAsset;
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

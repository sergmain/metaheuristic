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
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.company.CompanyCache;
import ai.metaheuristic.ai.dispatcher.data.ReplicationData;
import ai.metaheuristic.ai.dispatcher.repositories.AccountRepository;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRepository;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
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
 * Date: 10/1/2020
 * Time: 3:59 PM
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Profile("dispatcher")
public class ReplicationSourceHelperService {

    public final Globals globals;
    public final CompanyRepository companyRepository;
    public final AccountRepository accountRepository;
    public final SourceCodeRepository sourceCodeRepository;
    public final FunctionRepository functionRepository;
    public final SourceCodeCache sourceCodeCache;
    public final AccountCache accountCache;
    public final CompanyCache companyCache;

    private ReplicationData.AssetStateResponse currentAssets = null;
    private long mills = 0L;

    public ReplicationData.AssetStateResponse currentAssets() {
        if (currentAssets==null) {
            synchronized (this) {
                if (currentAssets==null) {
                    currentAssets = currentAssetsInternal();
                    mills = System.currentTimeMillis();
                    return currentAssets;
                }
            }
        }

        long currMills = System.currentTimeMillis();
        if (currMills-mills > 10_000) {
            synchronized (this) {
                if (currMills-mills > 10_000) {
                    currentAssets = currentAssetsInternal();
                    mills = System.currentTimeMillis();
                    return currentAssets;
                }
            }
        }
        return currentAssets;
    }

    private ReplicationData.AssetStateResponse currentAssetsInternal() {
        ReplicationData.AssetStateResponse res = new ReplicationData.AssetStateResponse();
        res.companies.addAll(companyRepository.findAllUniqueIds().stream()
                .map(id->{
                    Company company = companyCache.findByUniqueId(id);
                    if (company==null) {
                        return null;
                    }
                    CompanyParamsYaml params = company.getCompanyParamsYaml();
                    return new ReplicationData.CompanyShortAsset(company.uniqueId, params.updatedOn);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));

        res.usernames.addAll(accountRepository.findAllUsernames().stream()
                .map(username->{
                    Account account = accountCache.findByUsername(username);
                    if (account==null) {
                        return null;
                    }
                    return new ReplicationData.AccountShortAsset(account.username, account.updatedOn);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));

        res.functions.addAll(functionRepository.findAllFunctionCodes());
        res.sourceCodeUids.addAll(sourceCodeRepository.findAllAsIds().stream()
                .map(id->{
                    SourceCodeImpl sourceCode = sourceCodeCache.findById(id);
                    if (sourceCode==null || !sourceCode.valid) {
                        return null;
                    }
                    SourceCodeStoredParamsYaml params = sourceCode.getSourceCodeStoredParamsYaml();
                    // don't send an archived SourceCode
                    if (params.internalParams.archived) {
                        return null;
                    }
                    return sourceCode.uid;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
        return res;
    }

}

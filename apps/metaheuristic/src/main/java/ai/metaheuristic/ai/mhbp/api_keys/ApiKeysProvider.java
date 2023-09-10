/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.mhbp.api_keys;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.repositories.AccountRepository;
import ai.metaheuristic.ai.mhbp.provider.ProviderData;
import ai.metaheuristic.ai.mhbp.yaml.auth.ApiAuth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * @author Sergio Lissner
 * Date: 7/14/2023
 * Time: 11:38 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ApiKeysProvider {

    private final Globals globals;
    private final AccountRepository accountRepository;

    @Nullable
    public String getActualToken(ApiAuth.Auth auth, ProviderData.QueriedData queriedData) {
        return ApiKeysProviderUtils.getActualToken(auth, ()->accountRepository.findById(queriedData.userExecContext().accountId()).orElseThrow(), System::getProperty);
    }

}

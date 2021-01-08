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

package ai.metaheuristic.ai.dispatcher.account;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.repositories.AccountRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
@Profile("dispatcher")
@RequiredArgsConstructor
public class AccountCache {

    private final AccountRepository accountRepository;

//    @CachePut(cacheNames = Consts.ACCOUNTS_CACHE, key = "#result.username")
    public Account save(Account account) {
        TxUtils.checkTxExists();
        return accountRepository.save(account);
    }

//    @Cacheable(cacheNames = Consts.ACCOUNTS_CACHE, unless="#result==null")
    public @Nullable Account findByUsername(String username) {
        return accountRepository.findByUsername(username);
    }
}

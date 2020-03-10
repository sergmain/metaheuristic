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

package ai.metaheuristic.ai.dispatcher.account;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.repositories.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
@Profile("dispatcher")
@RequiredArgsConstructor
public class AccountCache {

    private final AccountRepository accountRepository;

    @CacheEvict(cacheNames = Consts.ACCOUNTS_CACHE, key = "#result.username")
    public Account save(Account account) {
        return accountRepository.save(account);
    }

    @Cacheable(cacheNames = Consts.ACCOUNTS_CACHE, unless="#result==null")
    public @Nullable Account findByUsername(String username) {
        return accountRepository.findByUsername(username);
    }
}

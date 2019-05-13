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

package ai.metaheuristic.ai.sec;

import ai.metaheuristic.ai.launchpad.beans.Account;
import ai.metaheuristic.ai.launchpad.repositories.AccountRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Profile("launchpad")
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Account getAccount(String username, String password, String token) {
        if (StringUtils.isAnyBlank(username, password, token)) {
            return null;
        }

        Account account = findByUsername(username);
        if (account == null || !token.equals(account.getToken())) {
            return null;
        }

        if (!passwordEncoder.matches(password, account.getPassword())) {
            return null;
        }
        return account;
    }

    @CachePut(cacheNames = "byUsername", key = "#account.username")
    public void save(Account account) {
        accountRepository.save(account);
    }

    @Cacheable(cacheNames = "byUsername", unless="#result==null")
    public Account findByUsername(String username) {
        //System.out.println("return non-cached result");
        return accountRepository.findByUsername(username);
    }
}

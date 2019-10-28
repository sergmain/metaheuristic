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

package ai.metaheuristic.ai.launchpad.account;

import ai.metaheuristic.ai.launchpad.beans.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 10/28/2019
 * Time: 1:07 AM
 */
@Service
@RequiredArgsConstructor
public class AccountLookupService {

//    @Autowired
//    @Qualifier("userDetailsService")
    private final UserDetailsService userDetailsService;

    public Account getAccount(Authentication authentication) {
        String username = (String) authentication.getPrincipal();
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        if (userDetails==null) {
            throw new UsernameNotFoundException("user not found: " + authentication.getPrincipal());
        }
        if (!(authentication.getCredentials()).equals(userDetails.getPassword())) {
            throw new BadCredentialsException("Bad credential");
        }

        String username = (String) authentication.getPrincipal();
        Account account = accountCache.findByUsername(username);
        if (account==null) {
            throw new UsernameNotFoundException("user not found: " + authentication.getPrincipal());
        }
        return account;
    }
}

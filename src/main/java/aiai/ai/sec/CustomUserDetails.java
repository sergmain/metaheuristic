/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.sec;

import aiai.ai.beans.Account;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigInteger;

/**
 * User: Serg
 * Date: 12.08.13
 * Time: 23:17
 */
@Service
public class CustomUserDetails implements UserDetailsService {

    private final AccountService accountService;
    @Value("${aiai.master-username}")
    private String masterUsername;
    @Value("${aiai.master-token}")
    private String masterToken;
    @Value("${aiai.master-password}")
    private String masterPassword;

    @Autowired
    public CustomUserDetails(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // strength - 10
        // pass     - 123
        // bcrypt   - $2a$10$jaQkP.gqwgenn.xKtjWIbeP4X.LDJx92FKaQ9VfrN2jgdOUTPTMIu

        ComplexUsername complexUsername = ComplexUsername.getInstance(username);
        if (complexUsername == null) {
            throw new UsernameNotFoundException("Username not found");
        }

        if (StringUtils.equals(masterUsername, complexUsername.getUsername()) && StringUtils.equals(masterToken, complexUsername.getToken())) {

            Account account = new Account();

            // fake Id, I hope it won't make any collision with real account
            // need to think of better solution for virtual accounts
            account.setId( BigInteger.valueOf( Integer.MAX_VALUE -5L) );
            account.setUsername(masterUsername);
            account.setToken(masterToken);
            account.setAccountNonExpired(true);
            account.setAccountNonLocked(true);
            account.setCredentialsNonExpired(true);
            account.setEnabled(true);
            account.setPassword(masterPassword);

            account.setAuthorities("ROLE_ADMIN, ROLE_MANAGER");
/*
            List<GrantedAuthority> authList = new ArrayList<>();
            authList.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            authList.add(new SimpleGrantedAuthority("ROLE_MANAGER"));
            account.setAuthorities(authList);
*/

            return account;
        }

        Account account = accountService.findByUsername(complexUsername.getUsername());
        if (account == null) {
            throw new UsernameNotFoundException("Username not found");
        }
        if (!complexUsername.getToken().equals(account.getToken())) {
            throw new UsernameNotFoundException("Username not found");
        }
        return account;
    }

    @Data
    public static class ComplexUsername {
        String username;
        String token;

        private ComplexUsername(String username, String token) {
            this.username = username;
            this.token = token;
        }

        public static ComplexUsername getInstance(String fullUsername) {
            int idx = fullUsername.lastIndexOf('=');
            if (idx == -1) {
                return null;
            }
            ComplexUsername complexUsername = new ComplexUsername(fullUsername.substring(0, idx), fullUsername.substring(idx + 1));

            return complexUsername.isValid() ? complexUsername : null;
        }

        private boolean isValid() {
            return username.length() > 0 && token.length() > 0;
        }
    }

}

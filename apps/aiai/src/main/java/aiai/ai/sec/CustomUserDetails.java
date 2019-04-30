/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
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

import aiai.ai.Globals;
import aiai.ai.launchpad.beans.Account;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * User: Serg
 * Date: 12.08.13
 * Time: 23:17
 */
@Service
@Profile("launchpad")
public class CustomUserDetails implements UserDetailsService {

    private final AccountService accountService;
    private final Globals globals;

    public final PasswordEncoder passwordEncoder;

    @Autowired
    public CustomUserDetails(AccountService accountService, Globals globals, PasswordEncoder passwordEncoder) {
        this.accountService = accountService;
        this.globals = globals;
        this.passwordEncoder = passwordEncoder;
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

        if (StringUtils.equals(globals.launchpadMasterUsername, complexUsername.getUsername()) && StringUtils.equals(globals.launchpadMasterToken, complexUsername.getToken())) {

            Account account = new Account();

            // fake Id, I hope it won't make any collision with real accounts
            // need to think of better solution for virtual accounts
            account.setId( Integer.MAX_VALUE -5L );
            account.setUsername(globals.launchpadMasterUsername);
            account.setToken(globals.launchpadMasterToken);
            account.setAccountNonExpired(true);
            account.setAccountNonLocked(true);
            account.setCredentialsNonExpired(true);
            account.setEnabled(true);
            account.setPassword(globals.launchpadMasterPassword);

            account.setRoles("ROLE_ADMIN, ROLE_MANAGER, ROLE_ACCESS_REST");
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

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

package ai.metaheuristic.ai.sec;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.mh.dispatcher..account.AccountCache;
import ai.metaheuristic.ai.mh.dispatcher..beans.Account;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * User: Serg
 * Date: 12.08.13
 * Time: 23:17
 */
@Service
@Profile("mh.dispatcher.")
@RequiredArgsConstructor
public class CustomUserDetails implements UserDetailsService {

    private final Globals globals;
    private final AccountCache accountService;

    @Data
    public static class ComplexUsername {
        String username;

        /**
         * won't delete this field for backward compatibility
         */
        @Deprecated
        final String token = "";

        private ComplexUsername(String username) {
            this.username = username;
        }

        public static ComplexUsername getInstance(String fullUsername) {
            int idx = fullUsername.lastIndexOf('=');
            final String username;
            if (idx == -1) {
                username = fullUsername;
            }
            else {
                username = fullUsername.substring(0, idx);
            }
            ComplexUsername complexUsername = new ComplexUsername(username);

            return complexUsername.isValid() ? complexUsername : null;
        }

        private boolean isValid() {
            return username!=null && !username.isBlank();
        }
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

        if (StringUtils.equals(globals.mh.dispatcher.MasterUsername, complexUsername.getUsername())) {

            Account account = new Account();

            // fake Id, I hope it won't make any collision with the real accounts
            // need to think of better solution for virtual accounts
            account.setId( Integer.MAX_VALUE -5L );

            // master admin will belong to companyUniqueId==1
            account.setCompanyId( 1L );
            account.setUsername(globals.mh.dispatcher.MasterUsername);
            account.setAccountNonExpired(true);
            account.setAccountNonLocked(true);
            account.setCredentialsNonExpired(true);
            account.setEnabled(true);
            account.setPassword(globals.mh.dispatcher.MasterPassword);

            account.setRoles(SecConsts.ROLE_MASTER_ADMIN);
            return account;
        }

        Account account = accountService.findByUsername(complexUsername.getUsername());
        if (account == null) {
            throw new UsernameNotFoundException("Username not found");
        }

        // fix role, the role ROLE_SERVER_REST_ACCESS can't be assigned to any user whose company isn't the master company with id==1
        if (!Consts.ID_1.equals(account.getCompanyId()) && account.getRolesAsList().contains(SecConsts.ROLE_SERVER_REST_ACCESS)) {
            account.getRolesAsList().remove(SecConsts.ROLE_SERVER_REST_ACCESS);
            String roles = String.join(", ", account.getRolesAsList());
            account.storeNewRole(roles);
        }

        return account;
    }
}

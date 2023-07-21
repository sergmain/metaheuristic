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

package ai.metaheuristic.ai.sec;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.account.AccountTxService;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
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
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class CustomUserDetails implements UserDetailsService {

    private final Globals globals;
    private final AccountTxService accountService;

    @Data
    public static class ComplexUsername {
        String username;

        /**
         * won't delete this field for backward compatibility
         */
        @SuppressWarnings("FieldMayBeStatic")
        @Deprecated
        final String token = "";

        private ComplexUsername(String username) {
            this.username = username;
        }

        @Nullable
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

        if (StringUtils.equals(globals.dispatcher.masterUsername, complexUsername.getUsername())) {

            Account account = new Account();

            // fake Id, I hope it won't make any collision with the real accounts
            // need to think of better solution for virtual accounts
            account.setId( Integer.MAX_VALUE -5L );

            // master admin will belong to companyUniqueId==1
            account.setCompanyId( 1L );
            account.setUsername(globals.dispatcher.masterUsername);
            account.setAccountNonExpired(true);
            account.setAccountNonLocked(true);
            account.setCredentialsNonExpired(true);
            account.setEnabled(true);
            account.setPassword(globals.dispatcher.masterPassword);

            account.setRoles(SecConsts.ROLE_MAIN_ADMIN);
            return account;
        }

        Account account = accountService.findByUsername(complexUsername.getUsername());
        if (account == null) {
            throw new UsernameNotFoundException("Username not found");
        }

        // TODO 2020-12-24 implement a common way to check roles over ai.metaheuristic.ai.sec.SecConsts.COMPANY_1_POSSIBLE_ROLES.
        // fix role, the role ROLE_SERVER_REST_ACCESS can't be assigned to any user whose company isn't the master company with id==1
        if (!Consts.ID_1.equals(account.getCompanyId()) && account.accountRoles.hasRole(SecConsts.ROLE_SERVER_REST_ACCESS)) {
            account.accountRoles.removeRole(SecConsts.ROLE_SERVER_REST_ACCESS);
        }

        return account;
    }
}

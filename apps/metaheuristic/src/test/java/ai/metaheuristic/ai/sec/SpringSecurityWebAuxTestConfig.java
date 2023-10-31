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

import ai.metaheuristic.ai.dispatcher.beans.Account;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.UserDetailsManager;

import java.util.*;

@TestConfiguration
public class SpringSecurityWebAuxTestConfig {

    private static final Long ADMIN_ID = 1L;

    private static final Long DATA_REST_ID = 2L;
    private static final Long DATA_ID = 3L;
    private static final Long ASSET_REST_ID = 4L;

    public static class MyUserDetailsManager implements UserDetailsManager {

        private final Map<String, Account> users = new HashMap<>();

        MyUserDetailsManager(Collection<Account> users) {
            for (Account user : users) {
                this.users.put(user.getUsername(), user);
            }
        }

        @Override
        public void createUser(UserDetails user) {
            throw new IllegalStateException("Not supported");
        }

        @Override
        public void updateUser(UserDetails user) {
            throw new IllegalStateException("Not supported");
        }

        @Override
        public void deleteUser(String username) {
            throw new IllegalStateException("Not supported");
        }

        @Override
        public void changePassword(String oldPassword, String newPassword) {
            throw new IllegalStateException("Not supported");
        }

        @Override
        public boolean userExists(String username) {
            return users.containsKey(username);
        }

        @Override
        public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
            Account account = users.get(username);
            return account;
        }
    }

    @Bean
    @Primary
    public UserDetailsService userDetailsService() {

        List<Account> accounts = new ArrayList<>();
        {
            Account account = new Account();

            account.setId(DATA_REST_ID);
            account.setUsername("data_rest");
            account.setAccountNonExpired(true);
            account.setAccountNonLocked(true);
            account.setCredentialsNonExpired(true);
            account.setEnabled(true);
            account.setPassword("123");
            account.setCompanyId(1L);

            account.setRoles("ROLE_SERVER_REST_ACCESS, ROLE_DATA");
            accounts.add(account);
        }
        {
            Account account = new Account();

            account.setId(ADMIN_ID);
            account.setUsername("admin");
            account.setAccountNonExpired(true);
            account.setAccountNonLocked(true);
            account.setCredentialsNonExpired(true);
            account.setEnabled(true);
            account.setPassword("123");

            account.setRoles("ROLE_SERVER_REST_ACCESS, ROLE_ADMIN");
            accounts.add(account);
        }
        {
            Account account = new Account();

            account.setId(DATA_ID);
            account.setUsername("data");
            account.setAccountNonExpired(true);
            account.setAccountNonLocked(true);
            account.setCredentialsNonExpired(true);
            account.setEnabled(true);
            account.setPassword("123");

            account.setRoles("ROLE_DATA");
            accounts.add(account);
        }
        {
            Account account = new Account();

            account.setId(ASSET_REST_ID);
            account.setUsername("asset_rest");
            account.setAccountNonExpired(true);
            account.setAccountNonLocked(true);
            account.setCredentialsNonExpired(true);
            account.setEnabled(true);
            account.setPassword("123");

            account.setRoles("ROLE_ASSET_REST_ACCESS");
            accounts.add(account);
        }

        return new MyUserDetailsManager(accounts);
    }
}

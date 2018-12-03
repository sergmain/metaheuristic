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

package aiai.ai.rest;

import aiai.ai.launchpad.beans.Account;
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

    private static final Long ADMIN_USER_ID = 1L;
    private static final Long USER_USER_ID = 2L;

    public static class MyUserDetailsManager implements UserDetailsManager {

        private Map<String, Account> users = new HashMap<>();

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
            return users.get(username);
        }
    }

    @Bean
    @Primary
    public UserDetailsService userDetailsService() {

        List<Account> accounts = new ArrayList<>();
        {
            Account account = new Account();

            account.setId(ADMIN_USER_ID);
            account.setUsername("admin");
            account.setToken("123");
            account.setAccountNonExpired(true);
            account.setAccountNonLocked(true);
            account.setCredentialsNonExpired(true);
            account.setEnabled(true);
            account.setPassword("123");

            account.setRoles("ROLE_ADMIN");
            accounts.add(account);
        }
        {
            Account account = new Account();

            account.setId(USER_USER_ID);
            account.setUsername("user");
            account.setToken("123");
            account.setAccountNonExpired(true);
            account.setAccountNonLocked(true);
            account.setCredentialsNonExpired(true);
            account.setEnabled(true);
            account.setPassword("123");

            account.setRoles("ROLE_ADMIN");
            accounts.add(account);
        }

        return new MyUserDetailsManager(accounts);
    }
}

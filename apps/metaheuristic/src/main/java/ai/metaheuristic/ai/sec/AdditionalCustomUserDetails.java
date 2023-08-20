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
import ai.metaheuristic.ai.dispatcher.beans.Account;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static ai.metaheuristic.ai.sec.SecConsts.ROLE_ASSET_REST_ACCESS;

/**
 * @author Sergio Lissner
 * Date: 8/18/2023
 * Time: 10:24 PM
 */
@Service
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class AdditionalCustomUserDetails {

    private final PasswordEncoder passwordEncoder;

    public String restUserPassword;
    public String restUserPasswordEncoded;
    public Account restUserAccount;

    @PostConstruct
    public void init() {
        restUserPassword = UUID.randomUUID().toString();
        restUserPasswordEncoded = passwordEncoder.encode(restUserPassword);

        restUserAccount = new Account();
        restUserAccount.setId( Integer.MAX_VALUE -6L );
        restUserAccount.setCompanyId( 1L );
        restUserAccount.setUsername(Consts.REST_USER);
        restUserAccount.setAccountNonExpired(true);
        restUserAccount.setAccountNonLocked(true);
        restUserAccount.setCredentialsNonExpired(true);
        restUserAccount.setEnabled(true);
        restUserAccount.setPassword(restUserPasswordEncoded);
        restUserAccount.setRoles(SecConsts.ROLE_SERVER_REST_ACCESS+", " + ROLE_ASSET_REST_ACCESS);
    }
}

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

package ai.metaheuristic.ai.dispatcher.rest.v1;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.data.AccountData;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Collection;

@RestController
@RequestMapping("/rest/v1")
@Profile("dispatcher")
@CrossOrigin
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class AuthUserRestController {

    private final Globals globals;

    // this end-point is used by angular's part only
    @RequestMapping("/user")
    public AccountData.UserData user(Principal user) {
        UsernamePasswordAuthenticationToken passwordAuthenticationToken = (UsernamePasswordAuthenticationToken) user;
        Account acc = (Account) passwordAuthenticationToken.getPrincipal();
        Collection<GrantedAuthority> authorities = passwordAuthenticationToken.getAuthorities();
        return new AccountData.UserData(acc.username, acc.getPublicName(), authorities, acc.companyId, globals.activeProfiles);
    }


}

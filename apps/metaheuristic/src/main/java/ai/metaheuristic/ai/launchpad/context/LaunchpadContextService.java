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

package ai.metaheuristic.ai.launchpad.context;

import ai.metaheuristic.ai.exceptions.BadExecutionContextException;
import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.ai.launchpad.beans.Account;
import ai.metaheuristic.ai.launchpad.beans.Company;
import ai.metaheuristic.ai.launchpad.company.CompanyCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 10/28/2019
 * Time: 1:07 AM
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LaunchpadContextService {

//    @Autowired
//    @Qualifier("userDetailsService")
    private final UserDetailsService userDetailsService;
    private final CompanyCache companyCache;

    public LaunchpadContext getContext(Authentication authentication) {
        String username = (String) authentication.getPrincipal();
        Account account = (Account)userDetailsService.loadUserByUsername(username);
        if (account==null) {
            throw new BadExecutionContextException("user not found: " + username);
        }
        Company company = companyCache.findById(account.companyId);
        if (company==null) {
            throw new BadExecutionContextException("company not found not found for user: " + username);
        }
        //noinspection UnnecessaryLocalVariable
        LaunchpadContext context = new LaunchpadContext(account, company);
        return context;
    }
}

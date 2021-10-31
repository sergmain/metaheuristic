/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.context;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.ai.dispatcher.company.CompanyCache;
import ai.metaheuristic.ai.exceptions.BadExecutionContextException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 10/28/2019
 * Time: 1:07 AM
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Profile("dispatcher")
public class UserContextService {

    private final CompanyCache companyCache;

    public DispatcherContext getContext(Authentication authentication) {
        Account account = (Account)authentication.getPrincipal();
        if (account==null) {
            throw new BadExecutionContextException("principal is null");
        }
        return getContext(authentication, account.companyId);
    }

    public DispatcherContext getContext(Authentication authentication, Long companyUniqueId) {
        Account account = (Account)authentication.getPrincipal();
        if (account==null) {
            throw new BadExecutionContextException("principal is null");
        }
        Company company = companyCache.findByUniqueId(companyUniqueId);
        if (company==null) {
            throw new BadExecutionContextException("company not found not found for user: " + account.username);
        }
        DispatcherContext context = new DispatcherContext(account, company);
        return context;
    }
}

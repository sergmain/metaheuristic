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

package ai.metaheuristic.ai.mhbp.rest;

import ai.metaheuristic.ai.mhbp.services.LocalGitRepoService;
import ai.metaheuristic.ai.mhbp.services.LocalGitSourcingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Sergio Lissner
 * Date: 4/14/2023
 * Time: 6:13 PM
 */
@RestController
@RequestMapping("/rest/v1/dispatcher/config")
@Slf4j
//@Profile("!stub-data")
//@CrossOrigin
@RequiredArgsConstructor
@Profile("dispatcher")
public class ConfigRestController {

    public final LocalGitSourcingService gitSourcingService;
    public final LocalGitRepoService gitRepoService;

    @PreAuthorize("hasAnyRole('MAIN_ADMIN', 'MAIN_OPERATOR', 'MAIN_SUPPORT', 'MANAGER')")
    @GetMapping("/info")
    public String info() {
        return ""+gitSourcingService.getGitStatus();
    }

}
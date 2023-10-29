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

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.status.DispatcherStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * @author Sergio Lissner
 * Date: 10/28/2023
 * Time: 9:04 PM
 */
@RestController
@RequestMapping("/rest/v1/dispatcher/status")
@Profile("dispatcher")
@CrossOrigin
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class StatusRestController {

    private final DispatcherStatusService dispatcherStatusService;
    private final UserContextService userContextService;

    @GetMapping("/source-code/{id}")
    public String sourceCode(@PathVariable Long id, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return dispatcherStatusService.statusSourceCode(id, context);
    }

    @GetMapping("/exec-context/{id}")
    public String execContext(@PathVariable Long id, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return dispatcherStatusService.statusExecContext(id, context);
    }
}

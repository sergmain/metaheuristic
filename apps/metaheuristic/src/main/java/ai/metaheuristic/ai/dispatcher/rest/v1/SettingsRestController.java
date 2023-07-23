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
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.data.ProcessorData;
import ai.metaheuristic.ai.dispatcher.data.SettingsData;
import ai.metaheuristic.ai.dispatcher.settings.SettingsService;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * @author Sergio Lissner
 * Date: 7/17/2023
 * Time: 11:07 PM
 */
@RestController
@RequestMapping("/rest/v1/dispatcher/settings")
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class SettingsRestController {

    private final SettingsService settingsService;
    private final UserContextService userContextService;

    @GetMapping("/api-keys")
    public SettingsData.ApiKeys apiKeys(Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return settingsService.getApiKeys(context);
    }

    @PostMapping("/save-openai-key-commit")
    public OperationStatusRest saveOpenaiKey(@RequestParam String openaiKey, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return settingsService.saveOpenaiKey(openaiKey, context);
    }

    @PostMapping("/change-password-commit")
    public OperationStatusRest changePasswordCommit(@RequestParam String oldPassword, @RequestParam String newPassword, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return settingsService.changePasswordCommit(oldPassword, newPassword, context);
    }


}

/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.data.SettingsData;
import ai.metaheuristic.ai.dispatcher.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Sergio Lissner
 * Date: 7/27/2023
 * Time: 2:50 AM
 */
@RestController
@RequestMapping("/rest/v1/dispatcher/anon")
@Profile("dispatcher")
@CrossOrigin
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class AnonymousUserRestController {

    private final SettingsService settingsService;

    @GetMapping("/ping")
    public String ping() {
        return "Metaheuristic";
    }

    /**
     * The dispatcher-wide list of supported locales, for the language selector on the index page
     * shown BEFORE login. Deliberately anonymous: the selector governs which client-side bundle the
     * login page renders in, and a login page nobody can read in their own language is the problem
     * being fixed. What it discloses is which UI locales an admin ticked - it names no account, no
     * company and no deployment detail.
     *
     * <p>Served from {@link ai.metaheuristic.ai.dispatcher.settings.SupportedLanguagesCache}, so an
     * unauthenticated caller cannot make the dispatcher do a params lookup per request.
     */
    @GetMapping("/languages")
    public SettingsData.Languages languages() {
        return settingsService.getLanguages();
    }
}

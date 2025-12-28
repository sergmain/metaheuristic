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

package ai.metaheuristic.ai.utils;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.commons.S;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

import static ai.metaheuristic.ai.Consts.*;

/**
 * @author Sergio Lissner
 * Date: 7/15/2023
 * Time: 11:18 PM
 */
@Slf4j
public class SpringHelpersUtils {

    public static final List<String> POSSIBLE_PROFILES = List.of(
        // Spring's profiles
        "dispatcher",
        "processor",
        "quickstart",
        STANDALONE_PROFILE, // standalone это профиль для запуска в режиме приложения на базе electron
        "disk-storage",
        "test", "disable-check-frontend",
        WEBSOCKET_PROFILE,

            // db's profiles
        "mysql", "postgresql", "h2", "hsqldb", "derby", "generic", "custom"
    );

    public static List<String> getProfiles(String activeProfiles) {
        List<String> profiles = Arrays.stream(StringUtils.split(activeProfiles, ", "))
                .filter(o -> !POSSIBLE_PROFILES.contains(o))
                .peek(o -> log.error(S.f("\n!!! Unknown profile: %s\n", o)))
                .toList();
        return profiles;
    }


}

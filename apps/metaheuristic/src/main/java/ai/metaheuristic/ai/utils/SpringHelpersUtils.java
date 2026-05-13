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

import ai.metaheuristic.commons.S;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static ai.metaheuristic.ai.Consts.STANDALONE_PROFILE;
import static ai.metaheuristic.ai.Consts.WEBSOCKET_PROFILE;

/**
 * @author Sergio Lissner
 * Date: 7/15/2023
 * Time: 11:18 PM
 */
@Slf4j
public class SpringHelpersUtils {

    // Concurrent set - plugins register their own profiles at load time from a static
    // initializer of their auto-config class. Reads happen during dispatcher startup
    // (checkProfiles in Config). Mixed read/write is rare but possible, so the storage
    // must be safe under it.
    private static final Set<String> POSSIBLE_PROFILES = ConcurrentHashMap.newKeySet();
    static {
        POSSIBLE_PROFILES.addAll(List.of(
            // Spring's profiles
            "dispatcher",
            "processor",
            "quickstart",
            STANDALONE_PROFILE, // standalone это профиль для запуска в режиме приложения на базе electron
            "external-storage",
            "disk-storage",
            "test", "disable-check-frontend",
            WEBSOCKET_PROFILE,
            "mcp",

            // db's profiles
            "mysql", "postgresql", "h2", "hsqldb", "derby", "generic", "custom"
        ));
    }

    /**
     * Read-only view of all currently registered profiles (base + everything plugins added).
     */
    public static Set<String> getPossibleProfiles() {
        return Collections.unmodifiableSet(POSSIBLE_PROFILES);
    }

    /**
     * Plugin extension point. Call from a plugin auto-config static initializer to
     * make the dispatcher accept the plugin's profile without complaining as "unknown".
     */
    public static void registerProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return;
        }
        if (POSSIBLE_PROFILES.add(profile)) {
            log.info("Registered plugin profile: {}", profile);
        }
    }

    public static List<String> getProfiles(String activeProfiles) {
        List<String> profiles = Arrays.stream(StringUtils.split(activeProfiles, ", "))
                .filter(o -> !POSSIBLE_PROFILES.contains(o))
                .peek(o -> log.error(S.f("\n!!! Unknown profile: %s\n", o)))
                .toList();
        return profiles;
    }


}

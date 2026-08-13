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
            "external-storage", // data of all Variables will be stored in external storage (not in DB)
            "disk-storage", // disk as external storage
            "test",
            "disable-check-frontend",
            WEBSOCKET_PROFILE, // turn on a notification of Processor by Dispatcher over websockets
            "mcp",

            // licence-manager backends. EXACTLY ONE must be active on a dispatcher: the LicenseSource
            // bean is selected by these and nothing else provides one, so a dispatcher started without
            // one has no licence backend at all. They are listed POSITIVELY - adding a backend must
            // never mean editing a negative @Profile expression somewhere else.
            "internal-lm",   // offline signed file, authority = us. The production backend.
            "mh-test-lm",    // this module's own test harness; its config lives in src/test.
            // NOT listed here: "aws-lm" and "rg-test-lm". A module owns its own profile names and
            // registers them from its auto-config static initializer, the same way java/aws already
            // owns "s3-storage". A profile that only means something when a jar is on the classpath
            // should only be ACCEPTED when that jar is present - and MH must not name RG at all.

            // db's profiles
            "mysql", "mariadb", "postgresql", "h2", "derby", "generic", "custom"
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

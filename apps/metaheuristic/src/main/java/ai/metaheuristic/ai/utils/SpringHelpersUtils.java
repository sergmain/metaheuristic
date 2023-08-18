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

package ai.metaheuristic.ai.utils;

import ai.metaheuristic.commons.S;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 7/15/2023
 * Time: 11:18 PM
 */
@Slf4j
public class SpringHelpersUtils {

    public static final List<String> POSSIBLE_PROFILES = List.of(
            // Spring's profiles
            "dispatcher", "processor", "quickstart", "standalone", "disk-storage", "test",

            // db's profiles
            "mysql", "postgresql", "h2", "generic", "custom");

    public static List<String> getProfiles(String activeProfiles) {
        List<String> profiles = Arrays.stream(StringUtils.split(activeProfiles, ", "))
                .filter(o -> !POSSIBLE_PROFILES.contains(o))
                .peek(o -> log.error(S.f("\n!!! Unknown profile: %s\n", o)))
                .toList();
        return profiles;
    }


}

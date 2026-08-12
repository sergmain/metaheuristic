/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.commons.json.license;

import ai.metaheuristic.api.data.license.LicenseInstallationParams;
import ai.metaheuristic.commons.json.versioning_json.AbstractParamsJsonUtils;
import ai.metaheuristic.commons.json.versioning_json.BaseJsonUtils;

import java.util.Map;

/**
 * <b>!!! BEFORE MAKING ANY EDITION IN THIS CLASS, READ /mnt/shared/metaheuristic.wiki/p/./multi-versioning-mechanic.md</b>
 * <br/>
 * Registry for the JSON multi-versioning chain backing {@code MH_LICENSE_INSTALLATION.PARAMS} — this dispatcher's identity.
 *
 * <p>Unlike the license CLAIMS chain, this payload is ordinary stored JSON rather than a JOSE
 * body, so the version detector can read it: callers use {@code BASE_JSON_UTILS.to(json)} and
 * {@code BASE_JSON_UTILS.toString(params)} directly.
 *
 * @author Serge
 */
public class LicenseInstallationParamsJsonUtils {

    private static final LicenseInstallationParamsJsonUtilsV1 UTILS_V_1 = new LicenseInstallationParamsJsonUtilsV1();
    private static final LicenseInstallationParamsJsonUtilsV1 DEFAULT_UTILS = UTILS_V_1;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static final BaseJsonUtils<LicenseInstallationParams> BASE_JSON_UTILS =
        new BaseJsonUtils<>(
            (Map) Map.of(
                1, (AbstractParamsJsonUtils) UTILS_V_1
            ),
            (AbstractParamsJsonUtils) DEFAULT_UTILS
        );
}

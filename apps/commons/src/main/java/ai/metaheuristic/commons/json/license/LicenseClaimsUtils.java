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

import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.data.license.LicenseClaims;
import ai.metaheuristic.commons.json.versioning_json.AbstractParamsJsonUtils;
import ai.metaheuristic.commons.json.versioning_json.BaseJsonUtils;

import java.util.Map;

/**
 * <b>!!! BEFORE MAKING ANY EDITION IN THIS CLASS, READ /mnt/shared/metaheuristic.wiki/p/./multi-versioning-mechanic.md</b>
 * <br/>
 * Registry for the JSON multi-versioning chain backing the signed-license payload.
 *
 * <p>The payload is a JOSE claims set, so the read path cannot use
 * {@link BaseJsonUtils#to(String)}: by the time a token has been parsed and its signature checked,
 * the caller holds a JWTClaimsSet and not the raw JSON the version detector expects. The verify
 * side therefore reads the {@code version} claim itself and calls {@link #fromJson(int, String)},
 * which is the one place the chain is entered on the read path.
 *
 * <p>Error code prefix: {@code 01.251.} (unique to this class).
 *
 * @author Serge
 */
public class LicenseClaimsUtils {

    private static final LicenseClaimsJsonUtilsV1 UTILS_V_1 = new LicenseClaimsJsonUtilsV1();
    private static final LicenseClaimsJsonUtilsV1 DEFAULT_UTILS = UTILS_V_1;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static final BaseJsonUtils<LicenseClaims> BASE_JSON_UTILS =
        new BaseJsonUtils<>(
            (Map) Map.of(
                1, (AbstractParamsJsonUtils) UTILS_V_1
            ),
            (AbstractParamsJsonUtils) DEFAULT_UTILS
        );

    /**
     * Enter the chain at an explicitly named version instead of letting the version detector sniff
     * the JSON. Deserializes with that version's frozen class and upgrades to the version-less
     * {@link LicenseClaims}.
     *
     * @param version the value of the payload's {@code version} claim
     * @param json    the payload JSON
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static LicenseClaims fromJson(int version, String json) {
        AbstractParamsJsonUtils jsonUtils = BASE_JSON_UTILS.getForVersion(version);
        if (jsonUtils==null) {
            throw new IllegalStateException("01.251.010 unsupported version of license claims: " + version);
        }
        BaseParams curr = jsonUtils.to(json);
        do {
            curr = jsonUtils.upgradeTo(curr);
        } while ((jsonUtils=(AbstractParamsJsonUtils)jsonUtils.nextUtil())!=null);

        final LicenseClaims p = (LicenseClaims) curr;
        p.checkIntegrity();
        return p;
    }
}

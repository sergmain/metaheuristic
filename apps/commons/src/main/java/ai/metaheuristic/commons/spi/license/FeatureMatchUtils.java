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

package ai.metaheuristic.commons.spi.license;

import java.util.Set;

/**
 * Feature matching against a granted key set. The single place matching is defined, shared by
 * every Entitlements implementation so the offline and external backends cannot drift apart on
 * gating semantics.
 *
 * Matching is exact and grant-only: a capability is licensed when it appears in the granted set,
 * and there is no wildcard. An unbounded grant reachable from one license would be unbounded
 * again under the union of several, so what is permitted is always enumerated at issuance.
 *
 * Total by construction: never throws, never rejects an unknown category.
 *
 * @author Serge
 */
public class FeatureMatchUtils {

    private FeatureMatchUtils() {
    }

    /** True when the granted set holds the feature exactly. */
    public static boolean matches(Set<String> grantedKeys, Feature f) {
        return grantedKeys.contains(f.key());
    }
}

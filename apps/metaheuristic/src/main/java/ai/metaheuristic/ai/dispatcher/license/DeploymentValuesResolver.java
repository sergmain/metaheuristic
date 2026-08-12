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

package ai.metaheuristic.ai.dispatcher.license;

import ai.metaheuristic.commons.spi.license.DeploymentValues;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Maps the active Spring profile set to the deployment values a license is checked against.
 *
 * <p><b>This is the ONE place profile expressions meet the license manager.</b> Profiles are the
 * DETECTION mechanism and never license content: a license names values (`H2`, `POSTGRES`, `S3`),
 * and this class answers which of them this dispatcher is actually running on. Putting the profile
 * set itself in a license was tried and rejected — a required-profiles list is a flat AND, so it
 * cannot say "any of H2/POSTGRES/MYSQL", and it tightens whenever a profile is added for an
 * unrelated reason.
 *
 * <p>The expressions mirrored here are the ones MH already selects its beans with:
 * <pre>
 * {@literal @}Profile({"dispatcher & postgresql"})                            -> POSTGRES
 * {@literal @}Profile({"dispatcher & (mysql | mariadb)"})                     -> MYSQL
 * {@literal @}Profile({"dispatcher & !mysql & !mariadb & !postgresql & ..."}) -> the embedded default, H2
 * {@literal @}Profile({"dispatcher & external-storage & s3-storage"})        -> S3
 * </pre>
 * If they ever change, they change here too — a drift between the two would license the wrong
 * deployment, which is worse than failing to license the right one.
 *
 * @author Serge
 */
public class DeploymentValuesResolver {

    public static final String POSTGRES = "POSTGRES";
    public static final String MYSQL = "MYSQL";
    public static final String H2 = "H2";
    public static final String S3 = "S3";

    private DeploymentValuesResolver() {
    }

    public static DeploymentValues resolve(Set<String> activeProfiles) {
        return new DeploymentValues(database(activeProfiles), storage(activeProfiles));
    }

    /**
     * H2 is the answer whenever no production datasource profile is active, because H2 IS the
     * embedded default — there is no "unknown database" case to model, and inventing one would
     * mean a deployment nothing could license.
     *
     * <p>Both {@code postgresql} and a MySQL profile active at once is a misconfiguration Spring
     * would already choke on; PostgreSQL wins here so the answer is at least deterministic.
     */
    public static String database(Set<String> activeProfiles) {
        if (activeProfiles.contains("postgresql")) {
            return POSTGRES;
        }
        if (activeProfiles.contains("mysql") || activeProfiles.contains("mariadb")) {
            return MYSQL;
        }
        return H2;
    }

    /**
     * Null when no external storage backend is active. That is NOT the same as running on a
     * storage backend no license grants: with nothing in use there is nothing to license, so the
     * axis is not checked at all.
     */
    @Nullable
    public static String storage(Set<String> activeProfiles) {
        return activeProfiles.contains("external-storage") && activeProfiles.contains("s3-storage") ? S3 : null;
    }
}

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

package ai.metaheuristic.commons.spi.license;

import org.jspecify.annotations.Nullable;

/**
 * The deployment values this dispatcher is actually running on, resolved by the adapter that owns
 * the Spring profile expressions and handed to the verify core as plain strings.
 *
 * Profiles are the DETECTION mechanism and never license content. MH already selects its datasource
 * and its storage backend by profile; the adapter maps the active profile set to one database value
 * and (when an external storage backend is active) one storage value, and the license manager
 * compares those values to the allow-lists a license grants. Passing the profile set itself would
 * put a flat AND-list where an OR of permitted values is needed, and would tighten the constraint
 * every time a profile is added for an unrelated reason.
 *
 * <p>{@code storage} is null when no external storage backend is in use - there is then nothing to
 * license on that axis, which is different from running on a storage backend no license grants.
 *
 * <p>Error code prefix: {@code 01.252.} (unique to this class).
 *
 * @author Serge
 */
public record DeploymentValues(String database, @Nullable String storage) {

    public DeploymentValues {
        if (database==null || database.isBlank()) {
            throw new IllegalArgumentException("01.252.010 the running database value must be non-blank");
        }
    }

    /** A deployment with no external storage backend active. */
    public static DeploymentValues of(String database) {
        return new DeploymentValues(database, null);
    }
}

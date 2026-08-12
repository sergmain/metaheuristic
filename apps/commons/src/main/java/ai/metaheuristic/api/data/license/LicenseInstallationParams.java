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

package ai.metaheuristic.api.data.license;

import ai.metaheuristic.api.data.BaseParams;
import lombok.Data;

/**
 * <b>!!! BEFORE MAKING ANY EDITION IN THIS CLASS, READ /mnt/shared/metaheuristic.wiki/p/./multi-versioning-mechanic.md</b>
 * <br/>
 * Version-less (current) schema of {@code MH_LICENSE_INSTALLATION.PARAMS} — the identity of THIS
 * dispatcher. Exactly one row exists, written once at first boot. Must remain field-for-field
 * identical to the latest versioned class ({@link LicenseInstallationParamsV1}).
 *
 * <p>{@link #installationId} is a random UUID. It is deliberately NOT derived from hardware, MAC
 * or hostname: re-hosting or scaling a deployment must not invalidate a license bound to it, and a
 * derived identity would do exactly that at the worst possible moment.
 *
 * <p>The DB row is authoritative. The value is mirrored best-effort to a file under
 * {@code ${mh.home}/config} for operator convenience; on disagreement the DB wins and the file is
 * rewritten, and deleting the file does not change the identity.
 */
@Data
public class LicenseInstallationParams implements BaseParams {

    @SuppressWarnings("FieldMayBeStatic")
    public final int version = 1;

    /** Random UUID minted once at first dispatcher boot. Compared against a license's installationId claim. */
    public String installationId;

    /** Epoch-millis the identity was minted. */
    public long createdOn;

    @Override
    public int getVersion() {
        return version;
    }
}

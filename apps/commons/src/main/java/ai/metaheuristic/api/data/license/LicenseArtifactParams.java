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
import org.jspecify.annotations.Nullable;

/**
 * <b>!!! BEFORE MAKING ANY EDITION IN THIS CLASS, READ /mnt/shared/metaheuristic.wiki/p/./multi-versioning-mechanic.md</b>
 * <br/>
 * Version-less (current) schema of {@code MH_LICENSE_ARTIFACT.PARAMS} — ONE installed license.
 * Must remain field-for-field identical to the latest versioned class
 * ({@link LicenseArtifactParamsV1}); business logic only ever sees this one.
 *
 * <p>The row is a container for {@link #token} and nothing more. Everything a reader might want
 * to know about the license — licensee, edition, grants, validity window — lives inside the signed
 * token and is read by verifying it. Copying any of that into columns or into this payload would
 * create a second, unsigned copy of facts the signature exists to protect, and the two would drift
 * the moment someone edited a row.
 *
 * <p>What IS stored here is only what the token cannot say: who installed it on this dispatcher
 * and when. That is provenance, not entitlement.
 *
 * <p>❗ {@link #installedOn} is audit/display only and MUST NOT become an input to validity.
 * Expiry runs from the signed {@code iat}; expiring relative to a locally-recorded first-seen
 * instant would reset every time the row was deleted and re-added.
 */
@Data
public class LicenseArtifactParams implements BaseParams {

    @SuppressWarnings("FieldMayBeStatic")
    public final int version = 1;

    /** The compact JWS, verbatim. The single source of truth about what this license grants. */
    public String token;

    /** Epoch-millis this license was installed on THIS dispatcher. Audit only — never validity. */
    public long installedOn;

    /** Which account installed it. Null when it was found in the license directory rather than uploaded. */
    @Nullable
    public Long installedByAccountId;

    /** Where it came from, so the admin page can say so. */
    public Origin origin = Origin.DB;

    /**
     * A license reaches a dispatcher one of two ways, and they are additive rather than a
     * precedence pair — under a SET of licenses there is nothing to break a tie.
     */
    public enum Origin {
        /** Uploaded through the admin UI and persisted as a row. */
        DB,
        /** Found as a *.jws file in the configured license directory. */
        DIRECTORY
    }

    @Override
    public int getVersion() {
        return version;
    }
}

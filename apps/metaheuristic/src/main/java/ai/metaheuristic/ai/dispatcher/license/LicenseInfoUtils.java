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

import ai.metaheuristic.api.data.license.LicenseArtifactParams;
import ai.metaheuristic.api.data.license.LicenseClaims;
import ai.metaheuristic.commons.spi.license.LicenseVerificationResult;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The per-license projection, as a pure function over verified results and whatever the database
 * knows about them.
 *
 * @author Serge
 */
public class LicenseInfoUtils {

    private LicenseInfoUtils() {
    }

    /** What a DB row contributes to the breakdown. Deliberately not the entity — this stays pure. */
    public record RowInfo(Long artifactId, LicenseArtifactParams.Origin origin, long installedOn) {
    }

    /**
     * Correlate each verified license back to its row BY TOKEN HASH, never by position.
     *
     * <p>The aggregate's list is built from DE-DUPLICATED tokens, so a license present both on
     * disk and in a row appears once and every later index shifts. A positional join would look
     * correct in every single-license test and silently mislabel every license after the first
     * duplicate in production.
     *
     * <p>A result with no matching row came from the license directory: a file on disk has no row,
     * and that absence is the only evidence of its origin there is.
     */
    public static List<LicenseInfoData.InstalledLicense> breakdown(
            List<LicenseVerificationResult> results, Map<String, RowInfo> rowsByTokenHash) {

        final List<LicenseInfoData.InstalledLicense> out = new ArrayList<>();
        for (LicenseVerificationResult r : results) {
            @Nullable final RowInfo row = rowsByTokenHash.get(LicenseTokenHashUtils.hash(r.token()));
            @Nullable final LicenseClaims claims = r.claims();

            out.add(new LicenseInfoData.InstalledLicense(
                    row==null ? null : row.artifactId(),
                    row==null ? LicenseArtifactParams.Origin.DIRECTORY : row.origin(),
                    r.state(),
                    claims==null ? null : claims.licensee,
                    claims==null ? null : claims.edition,
                    claims==null ? null : toMillis(claims.iat),
                    claims==null ? null : toMillis(claims.exp),
                    claims==null ? List.of() : List.copyOf(claims.capabilities),
                    claims==null ? List.of() : List.copyOf(claims.databases),
                    claims==null ? List.of() : List.copyOf(claims.storages),
                    row==null ? null : row.installedOn()));
        }
        return out;
    }

    @Nullable
    public static Long toMillis(@Nullable Instant instant) {
        return instant==null ? null : instant.toEpochMilli();
    }
}

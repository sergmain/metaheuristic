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
import java.util.Set;

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
     *
     * <p>UPDATE: "no row means the directory" is no longer the whole rule, because absence was
     * never evidence of PRESENCE. The same licence can be installed through BOTH channels - a
     * {@code .jws} lying in the licence directory and the identical token pasted into the UI - and
     * the token set is de-duplicated before verification, so the aggregate carries ONE result for
     * the pair. Reading origin off that single result reported only the row and made the copy on
     * disk vanish from the page: the admin deleted the row, the licence stayed in force, and
     * nothing on the screen said why.
     *
     * <p>So the breakdown is now one entry per INSTALLED ARTIFACT rather than one per verified
     * token: {@code directoryTokenHashes} names what is actually on disk, and a token found in both
     * places yields two entries - the file first, then the row. They necessarily carry the same
     * state and the same grants, because they are the same signed token; what differs is how each
     * one is retired, which is the only thing the admin needs the two entries for.
     */
    public static List<LicenseInfoData.InstalledLicense> breakdown(
            List<LicenseVerificationResult> results, Map<String, RowInfo> rowsByTokenHash,
            Set<String> directoryTokenHashes) {

        final List<LicenseInfoData.InstalledLicense> out = new ArrayList<>();
        for (LicenseVerificationResult r : results) {
            final String hash = LicenseTokenHashUtils.hash(r.token());
            final RowInfo row = rowsByTokenHash.get(hash);

            // on disk, or belonging to no row we can name - either way it is the directory copy.
            // The second case is the original fallback and stays: a result the installation is
            // holding must be listed even when neither channel claims it.
            if (directoryTokenHashes.contains(hash) || row==null) {
                out.add(entry(r, null, LicenseArtifactParams.Origin.DIRECTORY, null));
            }
            if (row!=null) {
                out.add(entry(r, row.artifactId(), row.origin(), row.installedOn()));
            }
        }
        return out;
    }

    /** One rendered line. The claims are the token's, the provenance is the channel's. */
    private static LicenseInfoData.InstalledLicense entry(
            LicenseVerificationResult r, @Nullable Long artifactId,
            LicenseArtifactParams.Origin origin, @Nullable Long installedOn) {

        final LicenseClaims claims = r.claims();
        return new LicenseInfoData.InstalledLicense(
                artifactId,
                origin,
                r.state(),
                claims==null ? null : claims.licensee,
                claims==null ? null : claims.edition,
                claims==null ? null : toMillis(claims.iat),
                claims==null ? null : toMillis(claims.exp),
                claims==null ? List.of() : List.copyOf(claims.capabilities),
                claims==null ? List.of() : List.copyOf(claims.databases),
                claims==null ? List.of() : List.copyOf(claims.storages),
                installedOn);
    }

    @Nullable
    public static Long toMillis(@Nullable Instant instant) {
        return instant==null ? null : instant.toEpochMilli();
    }
}

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

import ai.metaheuristic.commons.spi.license.LicenseState;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The two shapes the admin page renders. Kept apart on purpose.
 *
 * <p>An admin looking at one merged list cannot answer "which license is about to lapse, and what
 * do I lose when it does" — that question is the whole reason the breakdown exists beside the
 * aggregate. The effective entitlement says what this installation can do TODAY; the breakdown
 * says which license is paying for it.
 *
 * @author Serge
 */
public class LicenseInfoData {

    /**
     * What the installation is entitled to right now: the union of the valid licenses.
     *
     * <p>The grant lists are EMPTY unless {@code valid} — an unlicensed deployment licenses
     * nothing, and that is enforced where the union is computed rather than left to the renderer.
     */
    public record EffectiveEntitlement(
            LicenseType type,
            boolean valid,
            LicenseState state,
            List<String> capabilities,
            List<String> databases,
            List<String> storages,
            /** Latest exp among currently-valid licenses: when the installation loses ALL coverage. */
            @Nullable Long expiresAt,
            String installationId,
            /** What this dispatcher is actually running on, so a NOT_LICENSED state names the value. */
            String database,
            @Nullable String storage) {
    }

    /** One installed license, valid or not. Invalid ones stay listed — that is how an admin finds them. */
    public record InstalledLicense(
            /** MH_LICENSE_ARTIFACT.ID, or null when the license came from the license directory. */
            @Nullable Long artifactId,
            ai.metaheuristic.api.data.license.LicenseArtifactParams.Origin origin,
            LicenseState state,
            @Nullable String licensee,
            @Nullable String edition,
            @Nullable Long iat,
            @Nullable Long exp,
            /** This license's OWN grants, not the union — so an admin sees what it contributes. */
            List<String> capabilities,
            List<String> databases,
            List<String> storages,
            /** When it was installed HERE. Audit only, never an input to validity. */
            @Nullable Long installedOn) {
    }

    public record LicenseInfo(EffectiveEntitlement effective, List<InstalledLicense> licenses) {
    }
}

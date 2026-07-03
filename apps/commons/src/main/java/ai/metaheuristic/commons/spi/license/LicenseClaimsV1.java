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

import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The JSON payload of a signed license (the JWS body). This is NOT the operator YAML config.
 *
 * Claim-schema version lives in {@link #ver} and evolves via a small V1 -> V2 upgrade chain,
 * mirroring the MH *ParamsYaml philosophy but over JSON (the token is JOSE, not YAML). The
 * version marker deliberately does NOT distinguish the JOSE container (JWS vs JWE) - the
 * container is self-describing and detected structurally, before any claim is readable.
 *
 * Seal: 'features' are opaque key strings; MH stores them as data, never as symbols, and never
 * expands an 'edition' into a feature closure (that closure is proprietary and lives off-MH).
 *
 * @author Serge
 */
@Data
public class LicenseClaimsV1 {

    @SuppressWarnings("FieldMayBeStatic")
    public int ver = 1;

    public String licensee;

    // display/reporting claim only; never mapped to features inside MH.
    public String edition;

    // opaque feature-key strings, verbatim.
    public List<String> features = new ArrayList<>();

    @Nullable
    public Instant iat;

    @Nullable
    public Instant nbf;

    // absent exp == timeless; permitted ONLY alongside a non-empty requiredProfiles (Appendix D).
    @Nullable
    public Instant exp;

    // deployment pinning (section 7.7); opaque Spring-profile-name strings.
    public List<String> requiredProfiles = new ArrayList<>();

    public List<String> forbiddenProfiles = new ArrayList<>();

    @Nullable
    public String installationId;
}

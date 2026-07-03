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

package ai.metaheuristic.api.data.license;

import ai.metaheuristic.api.data.BaseParams;
import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Version-less (current) license-config schema - the only class business logic works with.
 * Operator-authored input recipe for the license-signer; NOT the license file. The 'signing'
 * section governs how/where to sign and never enters the token. MUST hold the same fields as the
 * highest-numbered LicenseConfigYamlV<N>.
 *
 * @author Serge
 */
@Data
public class LicenseConfigYaml implements BaseParams {

    @SuppressWarnings("FieldMayBeStatic")
    public final int version = 1;

    public License license = new License();
    public Signing signing = new Signing();

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    public static class License {
        public String licensee;
        // opaque display claim; edition -> feature closure is proprietary and computed off-MH.
        public String edition;
        // opaque feature-key strings, copied verbatim into the token.
        public List<String> features = new ArrayList<>();

        // validity. nbf optional. exp is EITHER 'expiresAt' (absolute ISO-8601 instant)
        // OR 'validityDuration' (ISO-8601 duration added to iat); never both; both absent == timeless.
        @Nullable public String notBefore;
        @Nullable public String expiresAt;
        @Nullable public String validityDuration;

        // deployment pinning (section 7.7); opaque Spring-profile-name strings.
        public List<String> requiredProfiles = new ArrayList<>();
        public List<String> forbiddenProfiles = new ArrayList<>();

        @Nullable public String installationId;
    }

    @Data
    public static class Signing {
        public String algorithm = "ES256";
        public String privateKeyFile;
        public String kid;
        public String outputFile;
    }
}

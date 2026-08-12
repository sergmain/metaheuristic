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
        // opaque 'Category:VALUE' capability keys, copied verbatim into the token.
        public List<String> capabilities = new ArrayList<>();

        // deployment axes: allow-lists of bare values, NOT composite keys - a database and a
        // storage backend are MH's own concepts, so there is no category to qualify them with.
        // An empty list grants nothing on that axis; it does not mean 'unconstrained'.
        public List<String> databases = new ArrayList<>();
        public List<String> storages = new ArrayList<>();

        // validity. nbf optional. exp is REQUIRED and is EITHER 'expiresAt' (absolute ISO-8601
        // instant) OR 'validityDuration' (ISO-8601 duration added to iat); never both, never neither.
        @Nullable public String notBefore;
        @Nullable public String expiresAt;
        @Nullable public String validityDuration;

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

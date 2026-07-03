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
 * Frozen V1 snapshot of the license-config schema. Immutable: its field structure must never change.
 * Deserialization goes through this class; LicenseConfigYamlUtilsV1.upgradeTo maps it to the
 * version-less LicenseConfigYaml.
 *
 * @author Serge
 */
@Data
public class LicenseConfigYamlV1 implements BaseParams {

    @SuppressWarnings("FieldMayBeStatic")
    public final int version = 1;

    public LicenseV1 license = new LicenseV1();
    public SigningV1 signing = new SigningV1();

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    public static class LicenseV1 {
        public String licensee;
        public String edition;
        public List<String> features = new ArrayList<>();
        @Nullable public String notBefore;
        @Nullable public String expiresAt;
        @Nullable public String validityDuration;
        public List<String> requiredProfiles = new ArrayList<>();
        public List<String> forbiddenProfiles = new ArrayList<>();
        @Nullable public String installationId;
    }

    @Data
    public static class SigningV1 {
        public String algorithm = "ES256";
        public String privateKeyFile;
        public String kid;
        public String outputFile;
    }
}

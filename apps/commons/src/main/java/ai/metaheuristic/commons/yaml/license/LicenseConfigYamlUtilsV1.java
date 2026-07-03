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

package ai.metaheuristic.commons.yaml.license;

import ai.metaheuristic.api.data.license.LicenseConfigYaml;
import ai.metaheuristic.api.data.license.LicenseConfigYamlV1;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BlankYamlParamsException;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.jspecify.annotations.NonNull;
import org.yaml.snakeyaml.Yaml;

/**
 * Latest (and only) license-config utility: deserializes V1 and upgrades it to the version-less
 * LicenseConfigYaml. End of the chain (nextUtil == null); downgrade unsupported.
 *
 * @author Serge
 */
public class LicenseConfigYamlUtilsV1
        extends AbstractParamsYamlUtils<LicenseConfigYamlV1, LicenseConfigYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(LicenseConfigYamlV1.class);
    }

    @NonNull
    @Override
    public LicenseConfigYaml upgradeTo(@NonNull LicenseConfigYamlV1 src) {
        src.checkIntegrity();
        final LicenseConfigYaml trg = new LicenseConfigYaml();

        trg.license.licensee = src.license.licensee;
        trg.license.edition = src.license.edition;
        trg.license.features = src.license.features;
        trg.license.notBefore = src.license.notBefore;
        trg.license.expiresAt = src.license.expiresAt;
        trg.license.validityDuration = src.license.validityDuration;
        trg.license.requiredProfiles = src.license.requiredProfiles;
        trg.license.forbiddenProfiles = src.license.forbiddenProfiles;
        trg.license.installationId = src.license.installationId;

        trg.signing.algorithm = src.signing.algorithm;
        trg.signing.privateKeyFile = src.signing.privateKeyFile;
        trg.signing.kid = src.signing.kid;
        trg.signing.outputFile = src.signing.outputFile;

        trg.checkIntegrity();
        return trg;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        throw new DowngradeNotSupportedException();
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull LicenseConfigYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public LicenseConfigYamlV1 to(@NonNull String yaml) {
        if (S.b(yaml)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        //noinspection UnnecessaryLocalVariable
        final LicenseConfigYamlV1 p = getYaml().load(yaml);
        return p;
    }
}

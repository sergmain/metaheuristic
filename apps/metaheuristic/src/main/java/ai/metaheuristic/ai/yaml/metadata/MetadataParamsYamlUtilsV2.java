/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.yaml.metadata;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.UpgradeNotSupportedException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 12/29/2020
 * Time: 1:29 AM
 */
public class MetadataParamsYamlUtilsV2
        extends AbstractParamsYamlUtils<MetadataParamsYamlV2, MetadataParamsYamlV3, MetadataParamsYamlUtilsV3, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(MetadataParamsYamlV2.class);
    }

    @NonNull
    @Override
    public MetadataParamsYamlV3 upgradeTo(@NonNull MetadataParamsYamlV2 src) {
        throw new UpgradeNotSupportedException("Upgrade of metadata.yaml from v2 to v3 isn't supported. Delete metadata.yaml and re-run Metaheuristic");
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
    }

    @Override
    public MetadataParamsYamlUtilsV3 nextUtil() {
        return (MetadataParamsYamlUtilsV3) MetadataParamsYamlUtils.BASE_YAML_UTILS.getForVersion(3);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull MetadataParamsYamlV2 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public MetadataParamsYamlV2 to(@NonNull String s) {
        if (S.b(s)) {
            return new MetadataParamsYamlV2();
        }
        final MetadataParamsYamlV2 p = getYaml().load(s);
        return p;
    }
}

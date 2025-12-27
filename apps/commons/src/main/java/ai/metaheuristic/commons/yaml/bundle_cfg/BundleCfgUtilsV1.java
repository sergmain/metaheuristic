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

package ai.metaheuristic.commons.yaml.bundle_cfg;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BlankYamlParamsException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.jspecify.annotations.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 8/19/2020
 * Time: 3:40 AM
 */
public class BundleCfgUtilsV1
        extends AbstractParamsYamlUtils<BundleCfgYamlV1, BundleCfgYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(BundleCfgYamlV1.class);
    }

    @NonNull
    @Override
    public BundleCfgYaml upgradeTo(@NonNull BundleCfgYamlV1 src) {
        src.checkIntegrity();
        BundleCfgYaml trg = new BundleCfgYaml();

        src.bundleConfig.stream().map(o->new BundleCfgYaml.BundleConfig(o.path, o.type)).collect(Collectors.toCollection(()->trg.bundleConfig));

        trg.checkIntegrity();
        return trg;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
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
    public String toString(@NonNull BundleCfgYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public BundleCfgYamlV1 to(@NonNull String yaml) {
        if (S.b(yaml)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        final BundleCfgYamlV1 p = getYaml().load(yaml);
        return p;
    }


}

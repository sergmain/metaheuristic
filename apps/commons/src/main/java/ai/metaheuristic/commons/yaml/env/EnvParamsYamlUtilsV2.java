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

package ai.metaheuristic.commons.yaml.env;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BlankYamlParamsException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import javax.annotation.Nonnull;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 1/4/2021
 * Time: 5:15 AM
 */
public class EnvParamsYamlUtilsV2
        extends AbstractParamsYamlUtils<EnvParamsYamlV2, EnvParamsYamlV3, EnvParamsYamlUtilsV3, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @Nonnull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(EnvParamsYamlV2.class);
    }

    @Nonnull
    @Override
    public EnvParamsYamlV3 upgradeTo(@Nonnull EnvParamsYamlV2 src) {
        src.checkIntegrity();
        EnvParamsYamlV3 trg = new EnvParamsYamlV3();

        trg.mirrors.putAll(src.mirrors);
        trg.envs.putAll(src.envs);
        src.disk.stream().map(o->new EnvParamsYamlV3.DiskStorageV3(o.code, o.path)).collect(Collectors.toCollection(() -> trg.disk));
        src.processors.stream().map(o->new EnvParamsYamlV3.ProcessorV3(o.code, o.tags)).collect(Collectors.toCollection(()->trg.processors));
        trg.quotas.disabled = true;
        trg.checkIntegrity();
        return trg;
    }

    @Nonnull
    @Override
    public Void downgradeTo(@Nonnull Void yaml) {
        return null;
    }

    @Override
    public EnvParamsYamlUtilsV3 nextUtil() {
        return (EnvParamsYamlUtilsV3) EnvParamsYamlUtils.BASE_YAML_UTILS.getForVersion(3);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@Nonnull EnvParamsYamlV2 yaml) {
        return getYaml().dump(yaml);
    }

    @Nonnull
    @Override
    public EnvParamsYamlV2 to(@Nonnull String yaml) {
        if (S.b(yaml)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        final EnvParamsYamlV2 p = getYaml().load(yaml);
        return p;
    }

}

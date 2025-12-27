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

package ai.metaheuristic.commons.yaml.env;

import ai.metaheuristic.api.ConstsApi;
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
public class EnvParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<EnvParamsYamlV1, EnvParamsYamlV2, EnvParamsYamlUtilsV2, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Nonnull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(EnvParamsYamlV1.class);
    }

    @Nonnull
    @Override
    public EnvParamsYamlV2 upgradeTo(@Nonnull EnvParamsYamlV1 src) {
        src.checkIntegrity();
        EnvParamsYamlV2 trg = new EnvParamsYamlV2();

        trg.mirrors.putAll(src.mirrors);
        trg.envs.putAll(src.envs);
        src.disk.stream().map(o->new EnvParamsYamlV2.DiskStorageV2(o.code, o.path)).collect(Collectors.toCollection(() -> trg.disk));
        trg.processors.add(new EnvParamsYamlV2.ProcessorV2(ConstsApi.DEFAULT_PROCESSOR_CODE, src.tags));

        trg.checkIntegrity();
        return trg;
    }

    @Nonnull
    @Override
    public Void downgradeTo(@Nonnull Void yaml) {
        return null;
    }

    @Override
    public EnvParamsYamlUtilsV2 nextUtil() {
        return (EnvParamsYamlUtilsV2) EnvParamsYamlUtils.BASE_YAML_UTILS.getForVersion(2);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@Nonnull EnvParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @Nonnull
    @Override
    public EnvParamsYamlV1 to(@Nonnull String yaml) {
        if (S.b(yaml)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        final EnvParamsYamlV1 p = getYaml().load(yaml);
        return p;
    }

}

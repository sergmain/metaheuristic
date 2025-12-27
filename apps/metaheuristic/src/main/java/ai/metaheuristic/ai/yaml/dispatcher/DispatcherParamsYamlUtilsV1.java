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

package ai.metaheuristic.ai.yaml.dispatcher;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;

import org.jspecify.annotations.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 4/19/2020
 * Time: 4:33 PM
 */
public class DispatcherParamsYamlUtilsV1
    extends AbstractParamsYamlUtils<DispatcherParamsYamlV1, DispatcherParamsYamlV2, DispatcherParamsYamlUtilsV2, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(DispatcherParamsYamlV1.class);
    }

    @NonNull
    @Override
    public DispatcherParamsYamlV2 upgradeTo(@NonNull DispatcherParamsYamlV1 v1) {
        DispatcherParamsYamlV2 t = new DispatcherParamsYamlV2();

        // stream is being used for possible future extension
        v1.batches.stream().collect(Collectors.toCollection(()->t.batches));
        v1.experiments.stream().collect(Collectors.toCollection(()->t.experiments));

        return t;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
    }

    @Override
    public DispatcherParamsYamlUtilsV2 nextUtil() {
        return (DispatcherParamsYamlUtilsV2) DispatcherParamsYamlUtils.BASE_YAML_UTILS.getForVersion(2);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull DispatcherParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public DispatcherParamsYamlV1 to(@NonNull String s) {
        final DispatcherParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}

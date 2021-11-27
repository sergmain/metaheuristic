/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.yaml.reduce_values_function;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BlankYamlParamsException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class ReduceVariablesConfigParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<ReduceVariablesConfigParamsYamlV1, ReduceVariablesConfigParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ReduceVariablesConfigParamsYamlV1.class);
    }

    @NonNull
    @Override
    public ReduceVariablesConfigParamsYaml upgradeTo(@NonNull ReduceVariablesConfigParamsYamlV1 src) {
        src.checkIntegrity();
        ReduceVariablesConfigParamsYaml trg = new ReduceVariablesConfigParamsYaml();
        trg.config = toConfig(src.config);

        trg.checkIntegrity();
        return trg;
    }

    private static ReduceVariablesConfigParamsYaml.Config toConfig(ReduceVariablesConfigParamsYamlV1.ConfigV1 v1) {
        ReduceVariablesConfigParamsYaml.Config cfg = new ReduceVariablesConfigParamsYaml.Config();
        cfg.reduces = v1.reduces.stream().map(o->new ReduceVariablesConfigParamsYaml.Reduce(o.policy, o.reducePercent, o.variable)).toList();
        cfg.fixName = v1.fixName;
        cfg.fittingVar = v1.fittingVar;
        cfg.metricsVar = v1.metricsVar;
        cfg.metricsName = v1.metricsName;
        cfg.reduceByValue.putAll(v1.reduceByValue);
        if (!v1.reduceByInstance.isEmpty()) {
            v1.reduceByInstance.stream().map(ReduceVariablesConfigParamsYamlUtilsV1::toByInstance).collect(Collectors.toCollection(()->cfg.reduceByInstance));
        }
        return cfg;
    }

    private static ReduceVariablesConfigParamsYaml.ByInstance toByInstance(ReduceVariablesConfigParamsYamlV1.ByInstanceV1 v1) {
        return new ReduceVariablesConfigParamsYaml.ByInstance(v1.input, v1.inputIs, v1.outputIs);
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
    public String toString(@NonNull ReduceVariablesConfigParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public ReduceVariablesConfigParamsYamlV1 to(@NonNull String s) {
        if (S.b(s)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        final ReduceVariablesConfigParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}

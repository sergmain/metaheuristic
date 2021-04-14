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

package ai.metaheuristic.ai.yaml.experiment_result;

import ai.metaheuristic.api.data.experiment_result.ExperimentResultParamsYaml;
import ai.metaheuristic.api.data.experiment_result.ExperimentResultParamsYamlV2;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 6/22/2019
 * Time: 11:36 PM
 */
public class ExperimentResultParamsYamlUtilsV2
        extends AbstractParamsYamlUtils<ExperimentResultParamsYamlV2, ExperimentResultParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ExperimentResultParamsYamlV2.class);
    }

    @NonNull
    @Override
    public ExperimentResultParamsYaml upgradeTo(@NonNull ExperimentResultParamsYamlV2 src) {
        src.checkIntegrity();
        ExperimentResultParamsYaml trg = new ExperimentResultParamsYaml();
        trg.createdOn = src.createdOn;
        trg.code = src.code;
        trg.name = src.name;
        trg.description = src.description;
        trg.maxValueCalculated = src.maxValueCalculated;
        trg.numberOfTask = src.numberOfTask;

        trg.execContext = new ExperimentResultParamsYaml.ExecContextWithParams(src.execContext.execContextId, src.execContext.execContextParams);
        trg.hyperParams.addAll(src.hyperParams);
        src.features.stream().map(this::toFeature).collect(Collectors.toCollection(()->trg.features));
        src.taskFeatures.stream().map(this::toTaskFeature).collect(Collectors.toCollection(()->trg.taskFeatures));
        src.parts.stream().map(this::toParts).collect(Collectors.toCollection(()->trg.parts));

        trg.checkIntegrity();
        return trg;
    }

    private ExperimentResultParamsYaml.ExperimentPart toParts(ExperimentResultParamsYamlV2.ExperimentPartV2 src) {
        ExperimentResultParamsYaml.ExperimentPart trg = new ExperimentResultParamsYaml.ExperimentPart();
        trg.taskContextId = src.taskContextId;
        trg.fitting = src.fitting;
        if (src.metrics!=null) {
            trg.metrics = new ExperimentResultParamsYaml.MetricValues(src.metrics.getValues());
        }
        trg.hyperParams = src.hyperParams;
        trg.featureVariables = src.featureVariables;
        return trg;
    }

    private ExperimentResultParamsYaml.ExperimentTaskFeature toTaskFeature(ExperimentResultParamsYamlV2.ExperimentTaskFeatureV2 src) {
        ExperimentResultParamsYaml.ExperimentTaskFeature etf = new ExperimentResultParamsYaml.ExperimentTaskFeature();

        etf.id = src.id;
        etf.execContextId = src.execContextId;
        etf.taskId = src.taskId;
        etf.featureId = src.featureId;
        etf.taskType = src.taskType;
        etf.metrics = new ExperimentResultParamsYaml.MetricValues(src.metrics.values);

        return etf;
    }

    private ExperimentResultParamsYaml.ExperimentFeature toFeature(ExperimentResultParamsYamlV2.ExperimentFeatureV2 src) {
        ExperimentResultParamsYaml.ExperimentFeature ef = new ExperimentResultParamsYaml.ExperimentFeature();
        ef.id = src.id;
        ef.variables = src.variables;
        ef.execStatus = src.execStatus;
        ef.experimentId = src.experimentId;
        ef.maxValues.putAll(src.maxValues);

        return ef;
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
    public String toString(@NonNull ExperimentResultParamsYamlV2 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public ExperimentResultParamsYamlV2 to(@NonNull String s) {
        final ExperimentResultParamsYamlV2 p = getYaml().load(s);
        return p;
    }

}

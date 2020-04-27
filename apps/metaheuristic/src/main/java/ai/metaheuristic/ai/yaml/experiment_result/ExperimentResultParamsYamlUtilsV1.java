/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
import ai.metaheuristic.api.data.experiment_result.ExperimentResultParamsYamlV1;
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
public class ExperimentResultParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<ExperimentResultParamsYamlV1, ExperimentResultParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ExperimentResultParamsYamlV1.class);
    }

    @NonNull
    @Override
    public ExperimentResultParamsYaml upgradeTo(@NonNull ExperimentResultParamsYamlV1 src, Long ... vars) {
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

        trg.checkIntegrity();
        return trg;
    }

    private ExperimentResultParamsYaml.ExperimentTaskFeature toTaskFeature(ExperimentResultParamsYamlV1.ExperimentTaskFeatureV1 src) {
        ExperimentResultParamsYaml.ExperimentTaskFeature etf = new ExperimentResultParamsYaml.ExperimentTaskFeature();

        etf.id = src.id;
        etf.execContextId = src.execContextId;
        etf.taskId = src.taskId;
        etf.featureId = src.featureId;
        etf.taskType = src.taskType;

        return etf;
    }

    private ExperimentResultParamsYaml.ExperimentFeature toFeature(ExperimentResultParamsYamlV1.ExperimentFeatureV1 src) {
        ExperimentResultParamsYaml.ExperimentFeature ef = new ExperimentResultParamsYaml.ExperimentFeature();
        ef.id = src.id;
        ef.variables = src.variables;
        ef.execStatus = src.execStatus;
        ef.experimentId = src.experimentId;
        ef.maxValue = src.maxValue;

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
    public String toString(ExperimentResultParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public ExperimentResultParamsYamlV1 to(String s) {
        final ExperimentResultParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}

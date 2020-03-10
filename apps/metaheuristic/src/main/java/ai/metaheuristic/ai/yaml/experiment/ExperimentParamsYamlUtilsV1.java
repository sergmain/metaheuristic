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

package ai.metaheuristic.ai.yaml.experiment;

import ai.metaheuristic.api.data.experiment.ExperimentParamsYamlV1;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 6/22/2019
 * Time: 11:36 PM
 */
public class ExperimentParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<ExperimentParamsYamlV1, ExperimentParamsYamlV1, ExperimentParamsYamlUtilsV2, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ExperimentParamsYamlV1.class);
    }

    @NonNull
    @Override
    public ExperimentParamsYamlV1 upgradeTo(@NonNull ExperimentParamsYamlV1 src, Long ... vars) {
        src.checkIntegrity();
        ExperimentParamsYamlV1 trg = new ExperimentParamsYamlV1();
        trg.createdOn = src.createdOn;
        BeanUtils.copyProperties(src.experimentYaml, trg.experimentYaml, "hyperParams");
        trg.experimentYaml.hyperParams = src.experimentYaml.hyperParams
                .stream()
                .map(o->new ExperimentParamsYamlV1.HyperParamV2(o.key, o.values, o.variants))
                .collect(Collectors.toList());

//        BeanUtils.copyProperties(src.processing, trg.processing, "taskFeatures", "features");
        trg.processing.isAllTaskProduced = src.processing.isAllTaskProduced;
        trg.processing.isFeatureProduced = src.processing.isFeatureProduced;
        trg.processing.maxValueCalculated = false;
        trg.processing.exportedToAtlas = false;
        trg.processing.numberOfTask = src.processing.numberOfTask;

        trg.processing.features = src.processing.features
                .stream()
                .map(o->{
                    ExperimentParamsYamlV1.ExperimentFeatureV2 f = new ExperimentParamsYamlV1.ExperimentFeatureV2();
                    BeanUtils.copyProperties(o, f);
                    return f;
                })
                .collect(Collectors.toList());

        trg.processing.taskFeatures = src.processing.taskFeatures
                .stream()
                .map(o->{
                    ExperimentParamsYamlV1.ExperimentTaskFeatureV2 f = new ExperimentParamsYamlV1.ExperimentTaskFeatureV2();
                    BeanUtils.copyProperties(o, f);
                    return f;
                })
                .collect(Collectors.toList());

        trg.checkIntegrity();
        return trg;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
    }

    @Override
    public ExperimentParamsYamlUtilsV2 nextUtil() {
        return (ExperimentParamsYamlUtilsV2)ExperimentParamsYamlUtils.BASE_YAML_UTILS.getForVersion(2);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(ExperimentParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public ExperimentParamsYamlV1 to(String s) {
        //noinspection UnnecessaryLocalVariable
        final ExperimentParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}

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

import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
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
public class ExperimentParamsYamlUtilsV2
        extends AbstractParamsYamlUtils<ExperimentParamsYamlV1, ExperimentParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ExperimentParamsYamlV1.class);
    }

    @NonNull
    @Override
    public ExperimentParamsYaml upgradeTo(@NonNull ExperimentParamsYamlV1 src, Long ... vars) {
        src.checkIntegrity();
        ExperimentParamsYaml trg = new ExperimentParamsYaml();
        trg.createdOn = src.createdOn;
        BeanUtils.copyProperties(src.experimentYaml, trg.experimentYaml, "hyperParams");
        trg.experimentYaml.hyperParams = src.experimentYaml.hyperParams
                .stream()
                .map(o->new ExperimentParamsYaml.HyperParam(o.key, o.values, o.variants))
                .collect(Collectors.toList());

//        BeanUtils.copyProperties(src.processing, trg.processing, "taskFeatures", "features");
        trg.processing.isAllTaskProduced = src.processing.isAllTaskProduced;
        trg.processing.isFeatureProduced = src.processing.isFeatureProduced;
        trg.processing.maxValueCalculated = src.processing.maxValueCalculated;
        trg.processing.exportedToAtlas = src.processing.exportedToAtlas ;
        trg.processing.numberOfTask = src.processing.numberOfTask;

        trg.processing.features = src.processing.features
                .stream()
                .map(o->{
                    ExperimentParamsYaml.ExperimentFeature f = new ExperimentParamsYaml.ExperimentFeature();
                    BeanUtils.copyProperties(o, f);
                    return f;
                })
                .collect(Collectors.toList());

        trg.processing.taskFeatures = src.processing.taskFeatures
                .stream()
                .map(o->{
                    ExperimentParamsYaml.ExperimentTaskFeature f = new ExperimentParamsYaml.ExperimentTaskFeature();
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
    public Void nextUtil() {
        return null;
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(ExperimentParamsYamlV1 yaml) {
        return null;
    }

    @NonNull
    @Override
    public ExperimentParamsYamlV1 to(String s) {
        //noinspection UnnecessaryLocalVariable
        final ExperimentParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}

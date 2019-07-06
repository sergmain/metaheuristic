/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYamlV1;
import ai.metaheuristic.api.data.plan.PlanParamsYamlV1;
import ai.metaheuristic.commons.yaml.YamlUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 6/22/2019
 * Time: 11:36 PM
 */
public class ExperimentParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<ExperimentParamsYamlV1, ExperimentParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    public Yaml getYaml() {
        return YamlUtils.init(ExperimentParamsYamlV1.class);
    }

    @Override
    public ExperimentParamsYaml upgradeTo(ExperimentParamsYamlV1 src) {
        ExperimentParamsYaml trg = new ExperimentParamsYaml();
        trg.createdOn = src.createdOn;
        BeanUtils.copyProperties(src.experimentYaml, trg.experimentYaml, "hyperParams");
        trg.experimentYaml.hyperParams = src.experimentYaml.hyperParams
                .stream()
                .map(o->new ExperimentParamsYaml.HyperParam(o.key, o.values, o.variants))
                .collect(Collectors.toList());

        BeanUtils.copyProperties(src.processing, trg.processing, "taskFeatures", "features");
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

        return trg;
    }

    @Override
    public Void downgradeTo(Void yaml) {
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

    public String toString(PlanParamsYamlV1 planYaml) {
        return getYaml().dump(planYaml);
    }

    public ExperimentParamsYamlV1 to(String s) {
        //noinspection UnnecessaryLocalVariable
        final ExperimentParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}

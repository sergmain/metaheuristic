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

package ai.metaheuristic.ai.yaml.atlas;

import ai.metaheuristic.api.data.atlas.AtlasParamsYamlV1;
import ai.metaheuristic.api.data.atlas.AtlasParamsYamlV2;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 6/22/2019
 * Time: 11:36 PM
 */
public class AtlasParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<AtlasParamsYamlV1, AtlasParamsYamlV2, AtlasParamsYamlUtilsV2, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    public Yaml getYaml() {
        return YamlUtils.init(AtlasParamsYamlV1.class);
    }

    @Override
    public AtlasParamsYamlV2 upgradeTo(AtlasParamsYamlV1 src) {
        src.checkIntegrity();
        AtlasParamsYamlV2 trg = new AtlasParamsYamlV2();
        trg.createdOn = src.createdOn;
        trg.plan = new AtlasParamsYamlV2.PlanWithParamsV2(src.plan.planId, src.plan.planParams);
        trg.workbook = new AtlasParamsYamlV2.WorkbookWithParamsV2(src.workbook.workbookId, src.workbook.workbookParams, src.workbook.execState);
        trg.experiment = new AtlasParamsYamlV2.ExperimentWithParamsV2(src.experiment.experimentId, src.experiment.experimentParams);
        trg.taskIds = src.tasks.stream().map(o->o.taskId).collect(Collectors.toList());

        trg.checkIntegrity();
        return trg;
    }

    @Override
    public Void downgradeTo(Void yaml) {
        return null;
    }

    @Override
    public AtlasParamsYamlUtilsV2 nextUtil() {
        return (AtlasParamsYamlUtilsV2)AtlasParamsYamlUtils.BASE_YAML_UTILS.getForVersion(2);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(AtlasParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    public AtlasParamsYamlV1 to(String s) {
        //noinspection UnnecessaryLocalVariable
        final AtlasParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}

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

import ai.metaheuristic.api.data.atlas.AtlasParamsYaml;
import ai.metaheuristic.api.data.atlas.AtlasParamsYamlV2;
import ai.metaheuristic.api.data.plan.PlanParamsYamlV1;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 6/22/2019
 * Time: 11:36 PM
 */
public class AtlasParamsYamlUtilsV2
        extends AbstractParamsYamlUtils<AtlasParamsYamlV2, AtlasParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    public Yaml getYaml() {
        return YamlUtils.init(AtlasParamsYamlV2.class);
    }

    @Override
    public AtlasParamsYaml upgradeTo(AtlasParamsYamlV2 src) {
        src.checkIntegrity();
        AtlasParamsYaml trg = new AtlasParamsYaml();
        trg.createdOn = src.createdOn;
        trg.plan = new AtlasParamsYaml.PlanWithParams(src.plan.planId, src.plan.planParams);
        trg.workbook = new AtlasParamsYaml.WorkbookWithParams(src.workbook.workbookId, src.workbook.workbookParams, src.workbook.execState);
        trg.experiment = new AtlasParamsYaml.ExperimentWithParams(src.experiment.experimentId, src.experiment.experimentParams);
        trg.taskIds = src.taskIds;

        trg.checkIntegrity();
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
    public String toString(AtlasParamsYamlV2 yaml) {
        return null;
    }

    public String toString(PlanParamsYamlV1 planYaml) {
        return getYaml().dump(planYaml);
    }

    public AtlasParamsYamlV2 to(String s) {
        //noinspection UnnecessaryLocalVariable
        final AtlasParamsYamlV2 p = getYaml().load(s);
        return p;
    }

}

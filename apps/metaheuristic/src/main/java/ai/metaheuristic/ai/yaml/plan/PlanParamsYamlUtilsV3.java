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

package ai.metaheuristic.ai.yaml.plan;

import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.data_storage.DataStorageParams;
import ai.metaheuristic.api.v1.launchpad.process.ProcessV3;
import ai.metaheuristic.commons.yaml.YamlUtils;
import org.yaml.snakeyaml.Yaml;

import static ai.metaheuristic.api.v1.data.PlanApiData.PlanParamsYaml;
import static ai.metaheuristic.api.v1.data.PlanApiData.PlanParamsYamlV3;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class PlanParamsYamlUtilsV3
        extends AbstractPlanParamsYamlUtils<PlanParamsYamlV3, PlanParamsYaml, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    public Yaml getYaml() {
        return YamlUtils.init(PlanParamsYamlV3.class);
    }

    @Override
    public PlanParamsYaml upgradeTo(PlanParamsYamlV3 yaml) {
        throw new IllegalStateException("There isn't a next yamlUtils");
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    public String toString(PlanParamsYamlV3 planYaml) {
        return getYaml().dump(planYaml);
    }

    public PlanParamsYamlV3 to(String s) {
        final PlanParamsYamlV3 p = getYaml().load(s);
        for (ProcessV3 process : p.planYaml.processes) {
            if (process.outputParams==null) {
                process.outputParams = new DataStorageParams(EnumsApi.DataSourcing.launchpad);
            }
        }
        return p;
    }


}

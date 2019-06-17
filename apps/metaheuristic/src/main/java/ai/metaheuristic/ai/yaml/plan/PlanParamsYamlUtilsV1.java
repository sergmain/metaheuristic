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
import ai.metaheuristic.api.v1.data.PlanApiData;
import ai.metaheuristic.api.v1.data_storage.DataStorageParams;
import ai.metaheuristic.api.v1.launchpad.process.ProcessV1;
import ai.metaheuristic.api.v1.launchpad.process.ProcessV2;
import ai.metaheuristic.commons.yaml.YamlUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

import static ai.metaheuristic.api.v1.data.PlanApiData.*;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class PlanParamsYamlUtilsV1
        extends AbstractPlanParamsYamlUtils<PlanParamsYamlV1, PlanParamsYamlV2, PlanParamsYamlUtilsV2> {

    @Override
    public int getVersion() {
        return 1;
    }

    public Yaml getYaml() {
        return YamlUtils.init(PlanParamsYamlV1.class);
    }

    @Override
    public PlanParamsYamlV2 upgradeTo(PlanParamsYamlV1 yaml) {
        PlanApiData.PlanParamsYamlV2 p = new PlanApiData.PlanParamsYamlV2();
        p.version = 2;
        p.planYaml = new PlanYamlV2();
        p.planYaml.metas = yaml.metas;
        p.planYaml.clean = yaml.clean;
        p.planYaml.processes = yaml.processes
                .stream()
                .map(o->{
                    ProcessV2 pV2 = new ProcessV2();
                    BeanUtils.copyProperties(o, pV2);
                    return pV2;
                })
                .collect(Collectors.toList());
        return p;
    }

    @Override
    public PlanParamsYamlUtilsV2 nextUtil() {
        return PlanParamsYamlUtilsFactory.YAML_UTILS_V_2;
    }

    public String toString(PlanParamsYamlV1 planYaml) {
        return getYaml().dump(planYaml);
    }

    public PlanParamsYamlV1 to(String s) {
        final PlanParamsYamlV1 p = getYaml().load(s);
        for (ProcessV1 process : p.processes) {
            if (process.outputParams==null) {
                process.outputParams = new DataStorageParams(EnumsApi.DataSourcing.launchpad);
            }
        }
        return p;
    }

}

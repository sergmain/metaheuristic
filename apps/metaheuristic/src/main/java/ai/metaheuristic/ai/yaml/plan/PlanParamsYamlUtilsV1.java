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

import ai.metaheuristic.ai.yaml.versioning.AbstractParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.plan.PlanParamsYamlV1;
import ai.metaheuristic.api.data.plan.PlanParamsYamlV2;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.api.launchpad.process.ProcessV1;
import ai.metaheuristic.api.launchpad.process.ProcessV2;
import ai.metaheuristic.commons.yaml.YamlUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class PlanParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<PlanParamsYamlV1, PlanParamsYamlV2, PlanParamsYamlUtilsV2, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    public Yaml getYaml() {
        return YamlUtils.init(PlanParamsYamlV1.class);
    }

    @Override
    public PlanParamsYamlV2 upgradeTo(PlanParamsYamlV1 yaml) {
        PlanParamsYamlV2 p = new PlanParamsYamlV2();
        p.planYaml = new PlanParamsYamlV2.PlanYamlV2();
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
    public Void downgradeTo(Void yaml) {
        return null;
    }

    @Override
    public PlanParamsYamlUtilsV2 nextUtil() {
        return (PlanParamsYamlUtilsV2)PlanParamsYamlUtils.BASE_YAML_UTILS.getForVersion(2);
    }

    @Override
    public Void prevUtil() {
        return null;
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

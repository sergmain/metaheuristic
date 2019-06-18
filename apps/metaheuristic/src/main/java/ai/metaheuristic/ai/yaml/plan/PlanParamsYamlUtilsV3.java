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
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.data.plan.PlanApiData;
import ai.metaheuristic.api.v1.data_storage.DataStorageParams;
import ai.metaheuristic.api.v1.launchpad.process.Process;
import ai.metaheuristic.api.v1.launchpad.process.ProcessV3;
import ai.metaheuristic.commons.yaml.YamlUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

import ai.metaheuristic.api.v1.data.plan.PlanParamsYaml;

import ai.metaheuristic.api.v1.data.plan.PlanParamsYamlV3;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class PlanParamsYamlUtilsV3
        extends AbstractParamsYamlUtils<PlanParamsYamlV3, PlanParamsYaml, Void> {

    @Override
    public int getVersion() {
        return 3;
    }

    public Yaml getYaml() {
        return YamlUtils.init(PlanParamsYamlV3.class);
    }

    @Override
    public PlanParamsYaml upgradeTo(PlanParamsYamlV3 pV3) {
        PlanParamsYaml p = new PlanParamsYaml();
        p.internalParams = pV3.internalParams;
        p.planYaml = new PlanParamsYaml.PlanYaml();
        if (pV3.planYaml.metas!=null){
            p.planYaml.metas = new ArrayList<>(pV3.planYaml.metas);
        }
        p.planYaml.clean = pV3.planYaml.clean;
        p.planYaml.processes = pV3.planYaml.processes.stream().map( o-> {
            Process pr = new Process();
            BeanUtils.copyProperties(o, pr);
            return pr;
        }).collect(Collectors.toList());
        return p;
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
        if (p.planYaml ==null) {
            throw new IllegalStateException("#635.010 Plan Yaml is null");
        }
        for (ProcessV3 process : p.planYaml.processes) {
            if (process.outputParams==null) {
                process.outputParams = new DataStorageParams(EnumsApi.DataSourcing.launchpad);
            }
        }
        if (p.internalParams==null) {
            p.internalParams = new PlanApiData.PlanInternalParamsYaml();
        }
        return p;
    }


}

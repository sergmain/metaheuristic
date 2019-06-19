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
import ai.metaheuristic.api.v1.data.plan.PlanParamsYamlV3;
import ai.metaheuristic.api.v1.data.plan.PlanParamsYamlV4;
import ai.metaheuristic.api.v1.data_storage.DataStorageParams;
import ai.metaheuristic.api.v1.launchpad.process.ProcessV3;
import ai.metaheuristic.api.v1.launchpad.process.ProcessV4;
import ai.metaheuristic.api.v1.launchpad.process.SnippetDefForPlanV4;
import ai.metaheuristic.commons.yaml.YamlUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class PlanParamsYamlUtilsV3
        extends AbstractParamsYamlUtils<PlanParamsYamlV3, PlanParamsYamlV4, PlanParamsYamlUtilsV4> {

    @Override
    public int getVersion() {
        return 3;
    }

    public Yaml getYaml() {
        return YamlUtils.init(PlanParamsYamlV3.class);
    }

    @Override
    public PlanParamsYamlV4 upgradeTo(PlanParamsYamlV3 pV3) {
        PlanParamsYamlV4 p = new PlanParamsYamlV4();
        p.internalParams = pV3.internalParams;
        p.planYaml = new PlanParamsYamlV4.PlanYamlV4();
        if (pV3.planYaml.metas!=null){
            p.planYaml.metas = new ArrayList<>(pV3.planYaml.metas);
        }
        p.planYaml.clean = pV3.planYaml.clean;
        p.planYaml.processes = pV3.planYaml.processes.stream().map( o-> {
            ProcessV4 pr = new ProcessV4();
            BeanUtils.copyProperties(o, pr, "snippetCodes", "preSnippetCode", "postSnippetCode");
            if (o.snippetCodes!=null) {
                pr.snippets = new ArrayList<>();
                for (String snippetCode : o.snippetCodes) {
                    pr.snippets.add(new SnippetDefForPlanV4(snippetCode) );
                }
            }
            if (o.preSnippetCode!=null) {
                pr.preSnippets = new ArrayList<>();
                for (String snippetCode : o.preSnippetCode) {
                    pr.preSnippets.add(new SnippetDefForPlanV4(snippetCode) );
                }
            }
            if (o.postSnippetCode!=null) {
                pr.postSnippets = new ArrayList<>();
                for (String snippetCode : o.postSnippetCode) {
                    pr.postSnippets.add(new SnippetDefForPlanV4(snippetCode) );
                }
            }
            return pr;
        }).collect(Collectors.toList());
        return p;
    }

    @Override
    public PlanParamsYamlUtilsV4 nextUtil() {
        return (PlanParamsYamlUtilsV4)PlanParamsYamlUtils.BASE_YAML_UTILS.getForVersion(4);
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

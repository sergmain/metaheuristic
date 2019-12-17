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

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.plan.PlanApiData;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.data.plan.PlanParamsYamlV6;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.api.launchpad.process.Process;
import ai.metaheuristic.api.launchpad.process.ProcessV6;
import ai.metaheuristic.api.launchpad.process.SnippetDefForPlan;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class PlanParamsYamlUtilsV6
        extends AbstractParamsYamlUtils<PlanParamsYamlV6, PlanParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 6;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(PlanParamsYamlV6.class);
    }

    @Override
    public PlanParamsYaml upgradeTo(PlanParamsYamlV6 v6, Long ... vars) {
        PlanParamsYaml p = new PlanParamsYaml();
        p.internalParams = v6.internalParams;
        p.planYaml = new PlanParamsYaml.PlanYaml();
        if (v6.planYaml.metas!=null){
            p.planYaml.metas = new ArrayList<>(v6.planYaml.metas);
        }
        p.planYaml.clean = v6.planYaml.clean;
        p.planYaml.processes = v6.planYaml.processes.stream().map( o-> {
            Process pr = new Process();
            BeanUtils.copyProperties(o, pr, "snippets", "preSnippets", "postSnippets");
            pr.snippets = o.snippets!=null ? o.snippets.stream().map(d->new SnippetDefForPlan(d.code, d.params)).collect(Collectors.toList()) : null;
            pr.preSnippets = o.preSnippets!=null ? o.preSnippets.stream().map(d->new SnippetDefForPlan(d.code, d.params)).collect(Collectors.toList()) : null;
            pr.postSnippets = o.postSnippets!=null ? o.postSnippets.stream().map(d->new SnippetDefForPlan(d.code, d.params)).collect(Collectors.toList()) : null;
            pr.metas = o.metas;
            return pr;
        }).collect(Collectors.toList());
        p.planYaml.planCode = v6.planYaml.planCode;
        if (v6.planYaml.ac!=null) {
            p.planYaml.ac = new PlanParamsYaml.AccessControl(v6.planYaml.ac.groups);
        }
        p.checkIntegrity();
        return p;
    }

    @Override
    public Void downgradeTo(Void yaml) {
        // not supported
        return null;
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public Void prevUtil() {
        // not supported
        return null;
    }

    @Override
    public String toString(PlanParamsYamlV6 planYaml) {
        return getYaml().dump(planYaml);
    }

    @Override
    public PlanParamsYamlV6 to(String s) {
        final PlanParamsYamlV6 p = getYaml().load(s);
        if (p.planYaml ==null) {
            throw new IllegalStateException("#635.010 Plan Yaml is null");
        }

        // fix default values
        for (ProcessV6 process : p.planYaml.processes) {
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

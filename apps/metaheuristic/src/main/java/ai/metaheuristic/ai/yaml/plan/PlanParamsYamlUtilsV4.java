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
import ai.metaheuristic.api.v1.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.v1.data.plan.PlanParamsYamlV4;
import ai.metaheuristic.api.v1.data_storage.DataStorageParams;
import ai.metaheuristic.api.v1.launchpad.process.Process;
import ai.metaheuristic.api.v1.launchpad.process.ProcessV4;
import ai.metaheuristic.api.v1.launchpad.process.SnippetDefForPlan;
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
public class PlanParamsYamlUtilsV4
        extends AbstractParamsYamlUtils<PlanParamsYamlV4, PlanParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 4;
    }

    public Yaml getYaml() {
        return YamlUtils.init(PlanParamsYamlV4.class);
    }

    @Override
    public PlanParamsYaml upgradeTo(PlanParamsYamlV4 pV4) {
        PlanParamsYaml p = new PlanParamsYaml();
        p.internalParams = pV4.internalParams;
        p.planYaml = new PlanParamsYaml.PlanYaml();
        if (pV4.planYaml.metas!=null){
            p.planYaml.metas = new ArrayList<>(pV4.planYaml.metas);
        }
        p.planYaml.clean = pV4.planYaml.clean;
        p.planYaml.processes = pV4.planYaml.processes.stream().map( o-> {
            Process pr = new Process();
            BeanUtils.copyProperties(o, pr, "snippets", "preSnippets", "postSnippets");
            pr.snippets = o.snippets!=null ? o.snippets.stream().map(d->new SnippetDefForPlan(d.code, d.params,d.paramsAsFile)).collect(Collectors.toList()) : null;
            pr.preSnippets = o.preSnippets!=null ? o.preSnippets.stream().map(d->new SnippetDefForPlan(d.code, d.params,d.paramsAsFile)).collect(Collectors.toList()) : null;
            pr.postSnippets = o.postSnippets!=null ? o.postSnippets.stream().map(d->new SnippetDefForPlan(d.code, d.params,d.paramsAsFile)).collect(Collectors.toList()) : null;
            return pr;
        }).collect(Collectors.toList());
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

    public String toString(PlanParamsYamlV4 planYaml) {
        return getYaml().dump(planYaml);
    }

    public PlanParamsYamlV4 to(String s) {
        final PlanParamsYamlV4 p = getYaml().load(s);
        if (p.planYaml ==null) {
            throw new IllegalStateException("#635.010 Plan Yaml is null");
        }

        // fix default values
        for (ProcessV4 process : p.planYaml.processes) {
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

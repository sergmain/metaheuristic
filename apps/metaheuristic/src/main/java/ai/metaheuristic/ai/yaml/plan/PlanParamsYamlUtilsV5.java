/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV5;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV6;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.api.launchpad.process.ProcessV5;
import ai.metaheuristic.api.launchpad.process.ProcessV6;
import ai.metaheuristic.api.launchpad.process.SnippetDefForPlanV6;
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
public class PlanParamsYamlUtilsV5
        extends AbstractParamsYamlUtils<SourceCodeParamsYamlV5, SourceCodeParamsYamlV6, PlanParamsYamlUtilsV6, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 5;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(SourceCodeParamsYamlV5.class);
    }

    @Override
    public SourceCodeParamsYamlV6 upgradeTo(SourceCodeParamsYamlV5 pV5, Long ... vars) {
        SourceCodeParamsYamlV6 p = new SourceCodeParamsYamlV6();
        p.internalParams = pV5.internalParams;
        p.planYaml = new SourceCodeParamsYamlV6.SourceCodeYamlV6();
        if (pV5.planYaml.metas!=null){
            p.planYaml.metas = new ArrayList<>(pV5.planYaml.metas);
        }
        p.planYaml.clean = pV5.planYaml.clean;
        p.planYaml.processes = pV5.planYaml.processes.stream().map( o-> {
            ProcessV6 pr = new ProcessV6();
            BeanUtils.copyProperties(o, pr, "snippets", "preSnippets", "postSnippets");
            pr.snippets = o.snippets!=null ? o.snippets.stream().map(d->new SnippetDefForPlanV6(d.code, d.params)).collect(Collectors.toList()) : null;
            pr.preSnippets = o.preSnippets!=null ? o.preSnippets.stream().map(d->new SnippetDefForPlanV6(d.code, d.params)).collect(Collectors.toList()) : null;
            pr.postSnippets = o.postSnippets!=null ? o.postSnippets.stream().map(d->new SnippetDefForPlanV6(d.code, d.params)).collect(Collectors.toList()) : null;
            pr.metas = o.metas;
            return pr;
        }).collect(Collectors.toList());
        p.planYaml.planCode = pV5.planYaml.planCode;
        p.checkIntegrity();
        return p;
    }

    @Override
    public Void downgradeTo(Void yaml) {
        // not supported
        return null;
    }

    @Override
    public PlanParamsYamlUtilsV6 nextUtil() {
        return (PlanParamsYamlUtilsV6)PlanParamsYamlUtils.BASE_YAML_UTILS.getForVersion(6);
    }

    @Override
    public Void prevUtil() {
        // not supported
        return null;
    }

    @Override
    public String toString(SourceCodeParamsYamlV5 planYaml) {
        return getYaml().dump(planYaml);
    }

    @Override
    public SourceCodeParamsYamlV5 to(String s) {
        final SourceCodeParamsYamlV5 p = getYaml().load(s);
        if (p.planYaml ==null) {
            throw new IllegalStateException("#635.010 SourceCode Yaml is null");
        }

        // fix default values
        for (ProcessV5 process : p.planYaml.processes) {
            if (process.outputParams==null) {
                process.outputParams = new DataStorageParams(EnumsApi.DataSourcing.launchpad);
            }
        }
        if (p.internalParams==null) {
            p.internalParams = new SourceCodeApiData.PlanInternalParamsYaml();
        }
        return p;
    }


}

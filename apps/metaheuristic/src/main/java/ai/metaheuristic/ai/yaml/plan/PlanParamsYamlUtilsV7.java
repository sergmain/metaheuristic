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
import ai.metaheuristic.api.data.plan.PlanParamsYamlV7;
import ai.metaheuristic.api.data.plan.PlanParamsYamlV8;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.commons.exceptions.UpgradeNotSupportedException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class PlanParamsYamlUtilsV7
        extends AbstractParamsYamlUtils<PlanParamsYamlV7, PlanParamsYamlV8, PlanParamsYamlUtilsV8, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 7;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(PlanParamsYamlV7.class);
    }

    @Override
    public PlanParamsYamlV8 upgradeTo(PlanParamsYamlV7 v7, Long ... vars) {
        throw new UpgradeNotSupportedException();
/*
        PlanParamsYamlV8 p = new PlanParamsYamlV8();
        p.internalParams = new PlanParamsYamlV8.InternalParamsV8(v7.internalParams.archived, v7.internalParams.published, v7.internalParams.updatedOn, null);
        p.plan = new PlanParamsYamlV8.PlanYamlV8();
        if (v7.planYaml.metas!=null){
            p.plan.metas = new ArrayList<>(v7.planYaml.metas);
        }
        p.plan.clean = v7.planYaml.clean;
        p.plan.processes = v7.planYaml.processes.stream().map(o-> {
            PlanParamsYamlV8.ProcessV8 pr = new PlanParamsYamlV8.ProcessV8();
            pr.name = o.name;
            pr.code = o.code;
            pr.type = o.type;
            pr.parallelExec = o.parallelExec;
            pr.timeoutBeforeTerminate = o.timeoutBeforeTerminate;
            pr.inputResourceCode = o.inputResourceCode;
            pr.outputParams = o.outputParams;
            pr.outputResourceCode = o.outputResourceCode;
            pr.order = o.order;

            pr.snippets = o.snippets!=null ? o.snippets.stream().map(d->new PlanParamsYamlV8.SnippetDefForPlanV8(d.code, d.params, EnumsApi.SnippetExecContext.external)).collect(Collectors.toList()) : null;
            pr.preSnippets = o.preSnippets!=null ? o.preSnippets.stream().map(d->new PlanParamsYamlV8.SnippetDefForPlanV8(d.code, d.params, EnumsApi.SnippetExecContext.external)).collect(Collectors.toList()) : null;
            pr.postSnippets = o.postSnippets!=null ? o.postSnippets.stream().map(d->new PlanParamsYamlV8.SnippetDefForPlanV8(d.code, d.params, EnumsApi.SnippetExecContext.external)).collect(Collectors.toList()) : null;
            pr.metas = o.metas;

            return pr;
        }).collect(Collectors.toList());
        p.plan.code = v7.planYaml.planCode;
        if (v7.planYaml.ac!=null) {
            p.plan.ac = new PlanParamsYamlV8.AccessControlV8(v7.planYaml.ac.groups);
        }
        p.originYaml = v7.originYaml;
        p.checkIntegrity();
        return p;
*/
    }

    @Override
    public Void downgradeTo(Void yaml) {
        // not supported
        return null;
    }

    @Override
    public PlanParamsYamlUtilsV8 nextUtil() {
        return (PlanParamsYamlUtilsV8)PlanParamsYamlUtils.BASE_YAML_UTILS.getForVersion(8);
    }

    @Override
    public Void prevUtil() {
        // not supported
        return null;
    }

    @Override
    public String toString(PlanParamsYamlV7 planYaml) {
        return getYaml().dump(planYaml);
    }

    @Override
    public PlanParamsYamlV7 to(String s) {
        final PlanParamsYamlV7 p = getYaml().load(s);
        if (p.planYaml ==null) {
            throw new IllegalStateException("#635.010 Plan Yaml is null");
        }

        // fix of default values
        for (PlanParamsYamlV7.ProcessV7 process : p.planYaml.processes) {
            if (process.outputParams==null) {
                process.outputParams = new DataStorageParams(EnumsApi.DataSourcing.launchpad);
            }
        }
        if (p.internalParams==null) {
            p.internalParams = new PlanParamsYamlV7.InternalParamsV7();
        }
        return p;
    }


}

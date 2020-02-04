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

import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.data.plan.PlanParamsYamlV8;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * @author Serge
 */
public class PlanParamsYamlUtilsV8
        extends AbstractParamsYamlUtils<PlanParamsYamlV8, PlanParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 8;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(PlanParamsYamlV8.class);
    }

    @Override
    public PlanParamsYaml upgradeTo(PlanParamsYamlV8 v8, Long ... vars) {
        PlanParamsYaml p = new PlanParamsYaml();
        p.internalParams = new PlanParamsYaml.InternalParams(v8.internalParams.archived, v8.internalParams.published, v8.internalParams.updatedOn, null);
        p.plan = new PlanParamsYaml.PlanYaml();
        if (v8.plan.metas!=null){
            p.plan.metas = new ArrayList<>(v8.plan.metas);
        }
        if (v8.plan.variables!=null) {
            p.plan.variables = new PlanParamsYaml.VariableDefinition(v8.plan.variables.global, v8.plan.variables.runtime);
            v8.plan.variables.inline.forEach(p.plan.variables.inline::put);
        }
        p.plan.clean = v8.plan.clean;
        p.plan.processes = v8.plan.processes.stream().map(o-> {
            PlanParamsYaml.Process pr = new PlanParamsYaml.Process();
            pr.name = o.name;
            pr.code = o.code;
            pr.timeoutBeforeTerminate = o.timeoutBeforeTerminate;
            o.input.stream().map(v->new PlanParamsYaml.Variable(v.sourcing, v.git, v.disk, v.name)).forEach(pr.input::add);
            o.output.stream().map(v->new PlanParamsYaml.Variable(v.sourcing, v.git, v.disk, v.name)).forEach(pr.output::add);
            pr.snippet = o.snippet!=null ? new PlanParamsYaml.SnippetDefForPlan(o.snippet.code, o.snippet.params, o.snippet.context) : null;
            pr.preSnippets = o.preSnippets!=null ? o.preSnippets.stream().map(d->new PlanParamsYaml.SnippetDefForPlan(d.code, d.params, d.context)).collect(Collectors.toList()) : null;
            pr.postSnippets = o.postSnippets!=null ? o.postSnippets.stream().map(d->new PlanParamsYaml.SnippetDefForPlan(d.code, d.params, d.context)).collect(Collectors.toList()) : null;
            pr.metas = o.metas;

            return pr;
        }).collect(Collectors.toList());
        p.plan.code = v8.plan.code;
        if (v8.plan.ac!=null) {
            p.plan.ac = new PlanParamsYaml.AccessControl(v8.plan.ac.groups);
        }
        p.originYaml = v8.originYaml;
        p.checkIntegrity();
        return p;
    }

    @Override
    public Void downgradeTo(Void yaml) {
        // not supported
        throw new DowngradeNotSupportedException();
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
    public String toString(PlanParamsYamlV8 planParamsYaml) {
        return getYaml().dump(planParamsYaml);
    }

    @Override
    public PlanParamsYamlV8 to(String s) {
        final PlanParamsYamlV8 p = getYaml().load(s);
        if (p.plan ==null) {
            throw new IllegalStateException("#635.010 Plan Yaml is null");
        }

        if (p.internalParams==null) {
            p.internalParams = new PlanParamsYamlV8.InternalParamsV8();
        }
        return p;
    }


}

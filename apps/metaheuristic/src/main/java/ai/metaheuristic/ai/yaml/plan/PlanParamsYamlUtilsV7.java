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
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV7;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV8;
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
        extends AbstractParamsYamlUtils<SourceCodeParamsYamlV7, SourceCodeParamsYamlV8, PlanParamsYamlUtilsV8, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 7;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(SourceCodeParamsYamlV7.class);
    }

    @Override
    public SourceCodeParamsYamlV8 upgradeTo(SourceCodeParamsYamlV7 v7, Long ... vars) {
        throw new UpgradeNotSupportedException();
/*
        SourceCodeParamsYamlV8 p = new SourceCodeParamsYamlV8();
        p.internalParams = new SourceCodeParamsYamlV8.InternalParamsV8(v7.internalParams.archived, v7.internalParams.published, v7.internalParams.updatedOn, null);
        p.sourceCode = new SourceCodeParamsYamlV8.SourceCodeV8();
        if (v7.sourceCodeYaml.metas!=null){
            p.sourceCode.metas = new ArrayList<>(v7.sourceCodeYaml.metas);
        }
        p.sourceCode.clean = v7.sourceCodeYaml.clean;
        p.sourceCode.processes = v7.sourceCodeYaml.processes.stream().map(o-> {
            SourceCodeParamsYamlV8.ProcessV8 pr = new SourceCodeParamsYamlV8.ProcessV8();
            pr.name = o.name;
            pr.code = o.code;
            pr.type = o.type;
            pr.parallelExec = o.parallelExec;
            pr.timeoutBeforeTerminate = o.timeoutBeforeTerminate;
            pr.inputResourceCode = o.inputResourceCode;
            pr.outputParams = o.outputParams;
            pr.outputResourceCode = o.outputResourceCode;
            pr.order = o.order;

            pr.snippets = o.snippets!=null ? o.snippets.stream().map(d->new SourceCodeParamsYamlV8.SnippetDefForPlanV8(d.code, d.params, EnumsApi.SnippetExecContext.external)).collect(Collectors.toList()) : null;
            pr.preSnippets = o.preSnippets!=null ? o.preSnippets.stream().map(d->new SourceCodeParamsYamlV8.SnippetDefForPlanV8(d.code, d.params, EnumsApi.SnippetExecContext.external)).collect(Collectors.toList()) : null;
            pr.postSnippets = o.postSnippets!=null ? o.postSnippets.stream().map(d->new SourceCodeParamsYamlV8.SnippetDefForPlanV8(d.code, d.params, EnumsApi.SnippetExecContext.external)).collect(Collectors.toList()) : null;
            pr.metas = o.metas;

            return pr;
        }).collect(Collectors.toList());
        p.sourceCode.code = v7.sourceCodeYaml.planCode;
        if (v7.sourceCodeYaml.ac!=null) {
            p.sourceCode.ac = new SourceCodeParamsYamlV8.AccessControlV8(v7.sourceCodeYaml.ac.groups);
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
    public String toString(SourceCodeParamsYamlV7 planYaml) {
        return getYaml().dump(planYaml);
    }

    @Override
    public SourceCodeParamsYamlV7 to(String s) {
        final SourceCodeParamsYamlV7 p = getYaml().load(s);
        if (p.planYaml ==null) {
            throw new IllegalStateException("#635.010 SourceCode Yaml is null");
        }

        // fix of default values
        for (SourceCodeParamsYamlV7.ProcessV7 process : p.planYaml.processes) {
            if (process.outputParams==null) {
                process.outputParams = new DataStorageParams(EnumsApi.DataSourcing.launchpad);
            }
        }
        if (p.internalParams==null) {
            p.internalParams = new SourceCodeParamsYamlV7.InternalParamsV7();
        }
        return p;
    }


}

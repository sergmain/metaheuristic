/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.yaml.source_code;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV2;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV3;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 10/24/2020
 * Time: 11:51 AM
 */
public class SourceCodeParamsYamlUtilsV2
        extends AbstractParamsYamlUtils<
        SourceCodeParamsYamlV2,
        SourceCodeParamsYamlV3,
        SourceCodeParamsYamlUtilsV3, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(SourceCodeParamsYamlV2.class);
    }

    @NonNull
    @Override
    public SourceCodeParamsYamlV3 upgradeTo(@NonNull SourceCodeParamsYamlV2 v2) {
        v2.checkIntegrity();

        SourceCodeParamsYamlV3 p = new SourceCodeParamsYamlV3();
        p.source = new SourceCodeParamsYamlV3.SourceCodeV3();
        p.source.instances = v2.source.instances;
        if (v2.source.metas!=null){
            p.source.metas = v2.source.metas;
        }
        if (v2.source.variables!=null) {
            p.source.variables = new SourceCodeParamsYamlV3.VariableDefinitionV3(v2.source.variables.globals);
            if (!S.b(v2.source.variables.startInputAs)) {
                p.source.variables.inputs.add(new SourceCodeParamsYamlV3.VariableV3(v2.source.variables.startInputAs, EnumsApi.DataSourcing.dispatcher));
            }
            p.source.variables.inline.putAll(v2.source.variables.inline);
        }
        p.source.clean = v2.source.clean;
        p.source.processes = v2.source.processes.stream().map(SourceCodeParamsYamlUtilsV2::toProcess).collect(Collectors.toList());

        p.source.uid = v2.source.uid;
        if (v2.source.ac!=null) {
            p.source.ac = new SourceCodeParamsYamlV3.AccessControlV3(v2.source.ac.groups);
        }
        p.checkIntegrity();
        return p;
    }

    @NonNull
    private static SourceCodeParamsYamlV3.ProcessV3 toProcess(SourceCodeParamsYamlV2.ProcessV2 o) {
        SourceCodeParamsYamlV3.ProcessV3 pr = new SourceCodeParamsYamlV3.ProcessV3();
        pr.name = o.name;
        pr.code = o.code;
        pr.timeoutBeforeTerminate = o.timeoutBeforeTerminate;
        o.inputs.stream().map(v->new SourceCodeParamsYamlV3.VariableV3(v.name, v.sourcing, v.git, v.disk, v.parentContext, v.array, v.type, v.getNullable(), v.ext)).forEach(pr.inputs::add);
        o.outputs.stream().map(v->new SourceCodeParamsYamlV3.VariableV3(v.name, v.sourcing, v.git, v.disk, v.parentContext, v.array, v.type, v.getNullable(), v.ext)).forEach(pr.outputs::add);
        pr.function = new SourceCodeParamsYamlV3.FunctionDefForSourceCodeV3(o.function.code, o.function.params, o.function.context);
        pr.preFunctions = o.preFunctions.stream().map(d->new SourceCodeParamsYamlV3.FunctionDefForSourceCodeV3(d.code, d.params, d.context)).collect(Collectors.toList());
        pr.postFunctions = o.postFunctions.stream().map(d->new SourceCodeParamsYamlV3.FunctionDefForSourceCodeV3(d.code, d.params, d.context)).collect(Collectors.toList());
        pr.metas = o.metas;
        if (o.cache!=null) {
            pr.cache = new SourceCodeParamsYamlV3.CacheV3(o.cache.enabled, o.cache.omitInline);
        }
        pr.tags = o.tags;
        pr.priority = o.priority;

        pr.subProcesses = o.subProcesses!=null
                ?  new SourceCodeParamsYamlV3.SubProcessesV3(
                o.subProcesses.logic, o.subProcesses.processes.stream().map(SourceCodeParamsYamlUtilsV2::toProcess).collect(Collectors.toList()) )
                : null;

        return pr;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        // not supported
        throw new DowngradeNotSupportedException();
    }

    @Override
    public SourceCodeParamsYamlUtilsV3 nextUtil() {
        return (SourceCodeParamsYamlUtilsV3) SourceCodeParamsYamlUtils.BASE_YAML_UTILS.getForVersion(3);
    }

    @Override
    public Void prevUtil() {
        // not supported
        return null;
    }

    @Override
    public String toString(@NonNull SourceCodeParamsYamlV2 sourceCodeParamsYaml) {
        return getYaml().dump(sourceCodeParamsYaml);
    }

    @NonNull
    @Override
    public SourceCodeParamsYamlV2 to(@NonNull String s) {
        final SourceCodeParamsYamlV2 p = getYaml().load(s);
        if (p.source ==null) {
            throw new IllegalStateException("#635.010 SourceCode Yaml is null");
        }
        return p;
    }


}

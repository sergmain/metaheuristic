/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV1;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV2;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 */
public class SourceCodeParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<
        SourceCodeParamsYamlV1,
        SourceCodeParamsYamlV2, SourceCodeParamsYamlUtilsV2, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(SourceCodeParamsYamlV1.class);
    }

    @NonNull
    @Override
    public SourceCodeParamsYamlV2 upgradeTo(@NonNull SourceCodeParamsYamlV1 v1) {
        v1.checkIntegrity();

        SourceCodeParamsYamlV2 p = new SourceCodeParamsYamlV2();
        p.source = new SourceCodeParamsYamlV2.SourceCodeV2();
        if (v1.source.metas!=null){
            p.source.metas = v1.source.metas;
        }
        if (v1.source.variables!=null) {
            p.source.variables = new SourceCodeParamsYamlV2.VariableDefinitionV2(v1.source.variables.globals, v1.source.variables.startInputAs);
            p.source.variables.inline.putAll(v1.source.variables.inline);
        }
        p.source.clean = v1.source.clean;
        p.source.processes = v1.source.processes.stream().map(SourceCodeParamsYamlUtilsV1::toProcess).collect(Collectors.toList());

        p.source.uid = v1.source.uid;
        if (v1.source.ac!=null) {
            p.source.ac = new SourceCodeParamsYamlV2.AccessControlV2(v1.source.ac.groups);
        }
        p.checkIntegrity();
        return p;
    }

    @NonNull
    private static SourceCodeParamsYamlV2.ProcessV2 toProcess(SourceCodeParamsYamlV1.ProcessV1 o) {
        SourceCodeParamsYamlV2.ProcessV2 pr = new SourceCodeParamsYamlV2.ProcessV2();
        pr.name = o.name;
        pr.code = o.code;
        pr.timeoutBeforeTerminate = o.timeoutBeforeTerminate;
        o.inputs.stream().map(v->new SourceCodeParamsYamlV2.VariableV2(v.sourcing, v.git, v.disk, v.name, v.parentContext, v.array, v.type, v.getNullable(), null)).forEach(pr.inputs::add);
        o.outputs.stream().map(v->new SourceCodeParamsYamlV2.VariableV2(v.sourcing, v.git, v.disk, v.name, v.parentContext, v.array, v.type, v.getNullable(), null)).forEach(pr.outputs::add);
        pr.function = new SourceCodeParamsYamlV2.FunctionDefForSourceCodeV2(o.function.code, o.function.params, o.function.context);
        pr.preFunctions = o.preFunctions.stream().map(d->new SourceCodeParamsYamlV2.FunctionDefForSourceCodeV2(d.code, d.params, d.context)).collect(Collectors.toList());
        pr.postFunctions = o.postFunctions.stream().map(d->new SourceCodeParamsYamlV2.FunctionDefForSourceCodeV2(d.code, d.params, d.context)).collect(Collectors.toList());
        pr.metas = o.metas;
        pr.cache = new SourceCodeParamsYamlV2.CacheV2(o.cacheOutput, false);
        pr.priority = 0;

        pr.subProcesses = o.subProcesses!=null
                ?  new SourceCodeParamsYamlV2.SubProcessesV2(
                    o.subProcesses.logic, o.subProcesses.processes.stream().map(SourceCodeParamsYamlUtilsV1::toProcess).collect(Collectors.toList()) )
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
    public SourceCodeParamsYamlUtilsV2 nextUtil() {
        return (SourceCodeParamsYamlUtilsV2) SourceCodeParamsYamlUtils.BASE_YAML_UTILS.getForVersion(2);
    }

    @Override
    public Void prevUtil() {
        // not supported
        return null;
    }

    @Override
    public String toString(@NonNull SourceCodeParamsYamlV1 sourceCodeParamsYaml) {
        return getYaml().dump(sourceCodeParamsYaml);
    }

    @NonNull
    @Override
    public SourceCodeParamsYamlV1 to(@NonNull String s) {
        final SourceCodeParamsYamlV1 p = getYaml().load(s);
        if (p.source ==null) {
            throw new IllegalStateException("#635.010 SourceCode Yaml is null");
        }
        return p;
    }


}

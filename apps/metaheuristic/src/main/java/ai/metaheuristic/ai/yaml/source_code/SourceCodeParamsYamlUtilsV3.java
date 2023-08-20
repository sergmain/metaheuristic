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

import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV3;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV4;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 06/10/2021
 */
public class SourceCodeParamsYamlUtilsV3
        extends AbstractParamsYamlUtils<
        SourceCodeParamsYamlV3,
        SourceCodeParamsYamlV4,
        SourceCodeParamsYamlUtilsV4, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 3;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(SourceCodeParamsYamlV3.class);
    }

    @NonNull
    @Override
    public SourceCodeParamsYamlV4 upgradeTo(@NonNull SourceCodeParamsYamlV3 v3) {
        v3.checkIntegrity();

        SourceCodeParamsYamlV4 p = new SourceCodeParamsYamlV4();
        p.source = new SourceCodeParamsYamlV4.SourceCodeV4();
        p.source.instances = v3.source.instances;
        if (v3.source.metas!=null){
            p.source.metas = v3.source.metas;
        }
        if (v3.source.variables!=null) {
            p.source.variables = new SourceCodeParamsYamlV4.VariableDefinitionV4(v3.source.variables.globals);
            toVariable(v3.source.variables.inputs, p.source.variables.inputs);
            toVariable(v3.source.variables.outputs, p.source.variables.outputs);
            p.source.variables.inline.putAll(v3.source.variables.inline);
        }
        p.source.clean = v3.source.clean;
        p.source.processes = v3.source.processes.stream().map(SourceCodeParamsYamlUtilsV3::toProcess).collect(Collectors.toList());

        p.source.uid = v3.source.uid;
        if (v3.source.ac!=null) {
            p.source.ac = new SourceCodeParamsYamlV4.AccessControlV4(v3.source.ac.groups);
        }
        p.source.strictNaming = v3.source.strictNaming;
        p.checkIntegrity();
        return p;
    }

    @NonNull
    private static SourceCodeParamsYamlV4.ProcessV4 toProcess(SourceCodeParamsYamlV3.ProcessV3 o) {
        SourceCodeParamsYamlV4.ProcessV4 pr = new SourceCodeParamsYamlV4.ProcessV4();
        pr.name = o.name;
        pr.code = o.code;
        pr.timeoutBeforeTerminate = o.timeoutBeforeTerminate;
        toVariable(o.inputs, pr.inputs);
        toVariable(o.outputs, pr.outputs);
        pr.function = new SourceCodeParamsYamlV4.FunctionDefForSourceCodeV4(o.function.code, o.function.params, o.function.context);
        pr.preFunctions = o.preFunctions.stream().map(d->new SourceCodeParamsYamlV4.FunctionDefForSourceCodeV4(d.code, d.params, d.context)).collect(Collectors.toList());
        pr.postFunctions = o.postFunctions.stream().map(d->new SourceCodeParamsYamlV4.FunctionDefForSourceCodeV4(d.code, d.params, d.context)).collect(Collectors.toList());
        pr.metas = o.metas;
        if (o.cache!=null) {
            pr.cache = new SourceCodeParamsYamlV4.CacheV4(o.cache.enabled, o.cache.omitInline);
        }
        pr.tag = o.tags;
        pr.priority = o.priority;
        pr.condition = o.condition;

        pr.subProcesses = o.subProcesses!=null
                ?  new SourceCodeParamsYamlV4.SubProcessesV4(
                o.subProcesses.logic, o.subProcesses.processes.stream().map(SourceCodeParamsYamlUtilsV3::toProcess).collect(Collectors.toList()) )
                : null;

        return pr;
    }

    private static void toVariable(List<SourceCodeParamsYamlV3.VariableV3> src, List<SourceCodeParamsYamlV4.VariableV4> trg) {
        src.stream().map(v -> new SourceCodeParamsYamlV4.VariableV4(v.name, v.getSourcing(), v.git, v.disk, v.parentContext, v.array, v.type, v.getNullable(), v.ext)).forEach(trg::add);
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        // not supported
        throw new DowngradeNotSupportedException();
    }

    @Override
    public SourceCodeParamsYamlUtilsV4 nextUtil() {
        return (SourceCodeParamsYamlUtilsV4) SourceCodeParamsYamlUtils.BASE_YAML_UTILS.getForVersion(4);
    }

    @Override
    public Void prevUtil() {
        // not supported
        return null;
    }

    @Override
    public String toString(@NonNull SourceCodeParamsYamlV3 sourceCodeParamsYaml) {
        return getYaml().dump(sourceCodeParamsYaml);
    }

    @NonNull
    @Override
    public SourceCodeParamsYamlV3 to(@NonNull String s) {
        final SourceCodeParamsYamlV3 p = getYaml().load(s);
        if (p.source ==null) {
            throw new IllegalStateException("#635.010 SourceCode Yaml is null");
        }
        return p;
    }


}

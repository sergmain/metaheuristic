/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.commons.yaml.source_code;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV4;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV5;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 01/30/2022
 */
public class SourceCodeParamsYamlUtilsV4
        extends AbstractParamsYamlUtils<
        SourceCodeParamsYamlV4,
        SourceCodeParamsYamlV5,
        SourceCodeParamsYamlUtilsV5, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 4;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(SourceCodeParamsYamlV4.class);
    }

    @NonNull
    @Override
    public SourceCodeParamsYamlV5 upgradeTo(@NonNull SourceCodeParamsYamlV4 v4) {
        v4.checkIntegrity();

        SourceCodeParamsYamlV5 p = new SourceCodeParamsYamlV5();
        p.source = new SourceCodeParamsYamlV5.SourceCodeV5();
        p.source.instances = v4.source.instances;
        if (v4.source.metas!=null){
            p.source.metas = v4.source.metas;
        }
        if (v4.source.variables!=null) {
            p.source.variables = new SourceCodeParamsYamlV5.VariableDefinitionV5(v4.source.variables.globals);
            toVariable(v4.source.variables.inputs, p.source.variables.inputs);
            toVariable(v4.source.variables.outputs, p.source.variables.outputs);
            p.source.variables.inline.putAll(v4.source.variables.inline);
        }
        p.source.clean = v4.source.clean;
        p.source.processes = v4.source.processes.stream().map(SourceCodeParamsYamlUtilsV4::toProcess).collect(Collectors.toList());

        p.source.uid = v4.source.uid;
        if (v4.source.ac!=null) {
            p.source.ac = new SourceCodeParamsYamlV5.AccessControlV5(v4.source.ac.groups);
        }
        p.source.strictNaming = v4.source.strictNaming;
        p.checkIntegrity();
        return p;
    }

    @NonNull
    private static SourceCodeParamsYamlV5.ProcessV5 toProcess(SourceCodeParamsYamlV4.ProcessV4 o) {
        SourceCodeParamsYamlV5.ProcessV5 pr = new SourceCodeParamsYamlV5.ProcessV5();
        pr.name = o.name;
        pr.code = o.code;
        pr.timeoutBeforeTerminate = o.timeoutBeforeTerminate;
        toVariable(o.inputs, pr.inputs);
        toVariable(o.outputs, pr.outputs);
        pr.function = new SourceCodeParamsYamlV5.FunctionDefForSourceCodeV5(o.function.code, o.function.params, o.function.context, EnumsApi.FunctionRefType.code);
        pr.preFunctions = o.preFunctions.stream().map(d->new SourceCodeParamsYamlV5.FunctionDefForSourceCodeV5(d.code, d.params, d.context, EnumsApi.FunctionRefType.code)).collect(Collectors.toList());
        pr.postFunctions = o.postFunctions.stream().map(d->new SourceCodeParamsYamlV5.FunctionDefForSourceCodeV5(d.code, d.params, d.context, EnumsApi.FunctionRefType.code)).collect(Collectors.toList());
        pr.metas = o.metas;
        if (o.cache!=null) {
            pr.cache = new SourceCodeParamsYamlV5.CacheV5(o.cache.enabled, o.cache.omitInline, false);
        }
        pr.tag = o.tag;
        pr.priority = o.priority;
        pr.condition = o.condition;
        pr.triesAfterError = o.triesAfterError;

        pr.subProcesses = o.subProcesses!=null
                ?  new SourceCodeParamsYamlV5.SubProcessesV5(
                o.subProcesses.logic, o.subProcesses.processes.stream().map(SourceCodeParamsYamlUtilsV4::toProcess).collect(Collectors.toList()) )
                : null;

        return pr;
    }

    private static void toVariable(List<SourceCodeParamsYamlV4.VariableV4> src, List<SourceCodeParamsYamlV5.VariableV5> trg) {
        src.stream().map(v -> new SourceCodeParamsYamlV5.VariableV5(v.name, v.getSourcing(), v.git, v.disk, v.parentContext, v.array, v.type, v.getNullable(), v.ext)).forEach(trg::add);
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        // not supported
        throw new DowngradeNotSupportedException();
    }

    @Override
    public SourceCodeParamsYamlUtilsV5 nextUtil() {
        return (SourceCodeParamsYamlUtilsV5) SourceCodeParamsYamlUtils.BASE_YAML_UTILS.getForVersion(5);
    }

    @Override
    public Void prevUtil() {
        // not supported
        return null;
    }

    @Override
    public String toString(@NonNull SourceCodeParamsYamlV4 sourceCodeParamsYaml) {
        return getYaml().dump(sourceCodeParamsYaml);
    }

    @NonNull
    @Override
    public SourceCodeParamsYamlV4 to(@NonNull String s) {
        final SourceCodeParamsYamlV4 p = getYaml().load(s);
        if (p.source ==null) {
            throw new IllegalStateException("#635.010 SourceCode Yaml is null");
        }
        return p;
    }


}

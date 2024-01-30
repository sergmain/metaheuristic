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

import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
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
public class SourceCodeParamsYamlUtilsV5
        extends AbstractParamsYamlUtils<
        SourceCodeParamsYamlV5,
        SourceCodeParamsYaml,
        Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 5;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(SourceCodeParamsYamlV5.class);
    }

    @NonNull
    @Override
    public SourceCodeParamsYaml upgradeTo(@NonNull SourceCodeParamsYamlV5 v4) {
        v4.checkIntegrity();

        SourceCodeParamsYaml p = new SourceCodeParamsYaml();
        p.source = new SourceCodeParamsYaml.SourceCode();
        p.source.instances = v4.source.instances;
        if (v4.source.metas!=null){
            p.source.metas = v4.source.metas;
        }
        if (v4.source.variables!=null) {
            p.source.variables = new SourceCodeParamsYaml.VariableDefinition(v4.source.variables.globals);
            toVariable(v4.source.variables.inputs, p.source.variables.inputs);
            toVariable(v4.source.variables.outputs, p.source.variables.outputs);
            p.source.variables.inline.putAll(v4.source.variables.inline);
        }
        p.source.clean = v4.source.clean;
        p.source.processes = v4.source.processes.stream().map(SourceCodeParamsYamlUtilsV5::toProcess).collect(Collectors.toList());

        p.source.uid = v4.source.uid;
        if (v4.source.ac!=null) {
            p.source.ac = new SourceCodeParamsYaml.AccessControl(v4.source.ac.groups);
        }
        p.source.strictNaming = v4.source.strictNaming;
        p.checkIntegrity();
        return p;
    }

    @NonNull
    private static SourceCodeParamsYaml.Process toProcess(SourceCodeParamsYamlV5.ProcessV5 o) {
        SourceCodeParamsYaml.Process pr = new SourceCodeParamsYaml.Process();
        pr.name = o.name;
        pr.code = o.code;
        pr.timeoutBeforeTerminate = o.timeoutBeforeTerminate;
        toVariable(o.inputs, pr.inputs);
        toVariable(o.outputs, pr.outputs);
        pr.function = new SourceCodeParamsYaml.FunctionDefForSourceCode(o.function.code, o.function.params, o.function.context, o.function.refType);
        pr.preFunctions = o.preFunctions.stream().map(d->new SourceCodeParamsYaml.FunctionDefForSourceCode(d.code, d.params, d.context, o.function.refType)).collect(Collectors.toList());
        pr.postFunctions = o.postFunctions.stream().map(d->new SourceCodeParamsYaml.FunctionDefForSourceCode(d.code, d.params, d.context, o.function.refType)).collect(Collectors.toList());
        pr.metas = o.metas;
        if (o.cache!=null) {
            pr.cache = new SourceCodeParamsYaml.Cache(o.cache.enabled, o.cache.omitInline, o.cache.cacheMeta);
        }
        pr.tag = o.tag;
        pr.priority = o.priority;
        pr.condition = o.condition;
        pr.triesAfterError = o.triesAfterError;

        pr.subProcesses = o.subProcesses!=null
                ?  new SourceCodeParamsYaml.SubProcesses(
                o.subProcesses.logic, o.subProcesses.processes.stream().map(SourceCodeParamsYamlUtilsV5::toProcess).collect(Collectors.toList()) )
                : null;

        return pr;
    }

    private static void toVariable(List<SourceCodeParamsYamlV5.VariableV5> src, List<SourceCodeParamsYaml.Variable> trg) {
        src.stream().map(v -> new SourceCodeParamsYaml.Variable(v.name, v.getSourcing(), v.git, v.disk, v.parentContext, v.array, v.type, v.getNullable(), v.ext)).forEach(trg::add);
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
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
    public String toString(@NonNull SourceCodeParamsYamlV5 sourceCodeParamsYaml) {
        return getYaml().dump(sourceCodeParamsYaml);
    }

    @NonNull
    @Override
    public SourceCodeParamsYamlV5 to(@NonNull String s) {
        final SourceCodeParamsYamlV5 p = getYaml().load(s);
        if (p.source ==null) {
            throw new IllegalStateException("#635.010 SourceCode Yaml is null");
        }
        return p;
    }


}

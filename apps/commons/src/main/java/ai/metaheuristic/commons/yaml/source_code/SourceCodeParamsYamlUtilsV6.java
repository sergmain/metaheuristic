/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV6;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 02/25/2026
 */
public class SourceCodeParamsYamlUtilsV6
        extends AbstractParamsYamlUtils<
        SourceCodeParamsYamlV6,
        SourceCodeParamsYaml,
        Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 6;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(SourceCodeParamsYamlV6.class);
    }

    @Override
    public SourceCodeParamsYaml upgradeTo(SourceCodeParamsYamlV6 v6) {
        v6.checkIntegrity();

        SourceCodeParamsYaml p = new SourceCodeParamsYaml();
        p.source = new SourceCodeParamsYaml.SourceCode();
        p.source.instances = v6.source.instances;
        if (v6.source.metas!=null){
            p.source.metas = v6.source.metas;
        }
        if (v6.source.variables!=null) {
            p.source.variables = new SourceCodeParamsYaml.VariableDefinition(v6.source.variables.globals);
            toVariable(v6.source.variables.inputs, p.source.variables.inputs);
            toVariable(v6.source.variables.outputs, p.source.variables.outputs);
            p.source.variables.inline.putAll(v6.source.variables.inline);
        }
        p.source.clean = v6.source.clean;
        if (v6.source.processes!=null) {
            p.source.processes = v6.source.processes.stream().map(SourceCodeParamsYamlUtilsV6::toProcess).collect(Collectors.toList());
        }

        p.source.uid = v6.source.uid;
        if (v6.source.ac!=null) {
            p.source.ac = new SourceCodeParamsYaml.AccessControl(v6.source.ac.groups);
        }
        p.source.strictNaming = v6.source.strictNaming;
        p.checkIntegrity();
        return p;
    }

    private static SourceCodeParamsYaml.Process toProcess(SourceCodeParamsYamlV6.ProcessV6 o) {
        SourceCodeParamsYaml.Process pr = new SourceCodeParamsYaml.Process();
        pr.name = o.name;
        pr.code = o.code;
        pr.timeoutBeforeTerminate = o.timeoutBeforeTerminate;
        toVariable(o.inputs, pr.inputs);
        toVariable(o.outputs, pr.outputs);
        pr.function = new SourceCodeParamsYaml.FunctionDefForSourceCode(o.function.code, o.function.params, o.function.context, o.function.refType);
        if (o.preFunctions!=null) {
            pr.preFunctions = o.preFunctions.stream().map(d -> new SourceCodeParamsYaml.FunctionDefForSourceCode(d.code, d.params, d.context, o.function.refType)).collect(Collectors.toList());
        }
        if (o.postFunctions!=null) {
            pr.postFunctions = o.postFunctions.stream().map(d -> new SourceCodeParamsYaml.FunctionDefForSourceCode(d.code, d.params, d.context, o.function.refType)).collect(Collectors.toList());
        }
        pr.metas = o.metas;
        if (o.cache!=null) {
            pr.cache = new SourceCodeParamsYaml.Cache(o.cache.enabled, o.cache.omitInline, o.cache.cacheMeta);
        }
        pr.tag = o.tag;
        pr.priority = o.priority;
        // V6 has ConditionV6, current has Condition - direct mapping
        if (o.condition!=null) {
            pr.condition = new SourceCodeParamsYaml.Condition(o.condition.conditions, o.condition.skipPolicy);
        }
        pr.triesAfterError = o.triesAfterError;

        pr.subProcesses = o.subProcesses!=null
                ?  new SourceCodeParamsYaml.SubProcesses(
                o.subProcesses.logic, o.subProcesses.processes!=null ? o.subProcesses.processes.stream().map(SourceCodeParamsYamlUtilsV6::toProcess).collect(Collectors.toList()) : null )
                : null;

        return pr;
    }

    private static void toVariable(List<SourceCodeParamsYamlV6.VariableV6> src, List<SourceCodeParamsYaml.Variable> trg) {
        src.stream().map(v -> new SourceCodeParamsYaml.Variable(v.name, v.getSourcing(), v.git, v.disk, v.parentContext, v.array, v.type, v.getNullable(), v.ext)).forEach(trg::add);
    }

    @Override
    public Void downgradeTo(Void yaml) {
        // not supported
        throw new DowngradeNotSupportedException();
    }

    @Override
    public @Nullable Void nextUtil() {
        return null;
    }

    @Override
    public @Nullable Void prevUtil() {
        // not supported
        return null;
    }

    @Override
    public String toString(SourceCodeParamsYamlV6 sourceCodeParamsYaml) {
        return getYaml().dump(sourceCodeParamsYaml);
    }

    @Override
    public SourceCodeParamsYamlV6 to(String s) {
        final SourceCodeParamsYamlV6 p = getYaml().load(s);
        if (p.source==null) {
            throw new IllegalStateException("636.010 SourceCode Yaml is null");
        }
        return p;
    }
}

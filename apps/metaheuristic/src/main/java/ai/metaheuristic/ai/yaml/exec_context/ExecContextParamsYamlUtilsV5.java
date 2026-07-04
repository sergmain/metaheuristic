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

package ai.metaheuristic.ai.yaml.exec_context;

import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYamlV5;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYamlV6;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;

import org.jspecify.annotations.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 01/30/2022
 * Time: 2:13 PM
 */
@SuppressWarnings("DuplicatedCode")
public class ExecContextParamsYamlUtilsV5
        extends AbstractParamsYamlUtils<ExecContextParamsYamlV5, ExecContextParamsYamlV6, ExecContextParamsYamlUtilsV6,
        Void, Void, Void> {

    @Override
    public int getVersion() {
        return 5;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ExecContextParamsYamlV5.class);
    }

    @NonNull
    @Override
    public ExecContextParamsYamlV6 upgradeTo(@NonNull ExecContextParamsYamlV5 v5) {
        ExecContextParamsYamlV6 t = new ExecContextParamsYamlV6();

        // right now we don't need to convert Graph because it has only one version of structure
        // so just copying of graph field is Ok
        t.clean = v5.clean;
        t.sourceCodeUid = v5.sourceCodeUid;
        t.desc = v5.desc;
        t.processesGraph = v5.processesGraph;
        v5.processes.stream().map(ExecContextParamsYamlUtilsV5::toProcess).collect(Collectors.toCollection(()->t.processes));
        initVariables(v5.variables, t.variables);
        if (v5.execContextGraph!=null) {
            t.execContextGraph = new ExecContextParamsYamlV6.ExecContextGraphV6(
                    v5.execContextGraph.rootExecContextId, v5.execContextGraph.parentExecContextId, v5.execContextGraph.graph);
        }
        t.columnNames.putAll(v5.columnNames);
        return t;
    }

    private static void initVariables(ExecContextParamsYamlV5.VariableDeclarationV5 v4, ExecContextParamsYamlV6.VariableDeclarationV6 v) {
        v.inline.putAll(v4.inline);
        v.globals = v4.globals;
        v4.inputs.forEach(o->v.inputs.add(toVariable(o)));
        v4.outputs.forEach(o->v.outputs.add(toVariable(o)));
    }

    private static ExecContextParamsYamlV6.ProcessV6 toProcess(ExecContextParamsYamlV5.ProcessV5 p2) {
        ExecContextParamsYamlV6.ProcessV6 p = new ExecContextParamsYamlV6.ProcessV6();
        p.function = toFunction(p2.function);
        p.preFunctions = p2.preFunctions!=null ? p2.preFunctions.stream().map(ExecContextParamsYamlUtilsV5::toFunction).collect(Collectors.toList()) : null;
        p.postFunctions = p2.postFunctions!=null ? p2.postFunctions.stream().map(ExecContextParamsYamlUtilsV5::toFunction).collect(Collectors.toList()) : null;
        p2.inputs.stream().map(ExecContextParamsYamlUtilsV5::toVariable).collect(Collectors.toCollection(()->p.inputs));
        p2.outputs.stream().map(ExecContextParamsYamlUtilsV5::toVariable).collect(Collectors.toCollection(()->p.outputs));
        p.metas.addAll(p2.metas);
        if (p2.cache!=null) {
            p.cache = new ExecContextParamsYamlV6.CacheV6(p2.cache.enabled, p2.cache.omitInline, p2.cache.cacheMeta);
        }
        p.processName = p2.processName;
        p.processCode = p2.processCode;
        p.internalContextId = p2.internalContextId;
        p.logic = p2.logic;
        p.timeoutBeforeTerminate = p2.timeoutBeforeTerminate;
        p.tag = p2.tag;
        p.priority = p2.priority;
        p.condition = p2.condition;
        p.triesAfterError = p2.triesAfterError;
        return p;
    }

    private static ExecContextParamsYamlV6.VariableV6 toVariable(ExecContextParamsYamlV5.VariableV5 v) {
        return new ExecContextParamsYamlV6.VariableV6(v.name, v.context, v.sourcing, v.git, v.disk, v.parentContext, v.type, v.getNullable(), v.ext, null);
    }

    private static ExecContextParamsYamlV6.FunctionDefinitionV6 toFunction(ExecContextParamsYamlV5.FunctionDefinitionV5 f1) {
        return new ExecContextParamsYamlV6.FunctionDefinitionV6(f1.code, f1.params, f1.context, f1.refType);
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
    }

    @Override
    public ExecContextParamsYamlUtilsV6 nextUtil() {
        return (ExecContextParamsYamlUtilsV6) ExecContextParamsYamlUtils.BASE_YAML_UTILS.getForVersion(6);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull ExecContextParamsYamlV5 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public ExecContextParamsYamlV5 to(@NonNull String s) {
        final ExecContextParamsYamlV5 p = getYaml().load(s);
        return p;
    }
}

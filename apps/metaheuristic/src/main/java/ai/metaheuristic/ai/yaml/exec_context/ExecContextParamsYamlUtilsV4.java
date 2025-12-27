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

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYamlV4;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYamlV5;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;

import org.jspecify.annotations.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 6/26/2021
 * Time: 2:13 PM
 */
@SuppressWarnings("DuplicatedCode")
public class ExecContextParamsYamlUtilsV4
        extends AbstractParamsYamlUtils<ExecContextParamsYamlV4, ExecContextParamsYamlV5, ExecContextParamsYamlUtilsV5,
        Void, Void, Void> {

    @Override
    public int getVersion() {
        return 4;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ExecContextParamsYamlV4.class);
    }

    @NonNull
    @Override
    public ExecContextParamsYamlV5 upgradeTo(@NonNull ExecContextParamsYamlV4 v4) {
        ExecContextParamsYamlV5 t = new ExecContextParamsYamlV5();

        // right now we don't need to convert Graph because it has only one version of structure
        // so just copying of graph field is Ok
        t.clean = v4.clean;
        t.sourceCodeUid = v4.sourceCodeUid;
        t.processesGraph = v4.processesGraph;
        v4.processes.stream().map(ExecContextParamsYamlUtilsV4::toProcess).collect(Collectors.toCollection(()->t.processes));
        initVariables(v4.variables, t.variables);
        if (v4.execContextGraph!=null) {
            t.execContextGraph = new ExecContextParamsYamlV5.ExecContextGraphV5(
                    v4.execContextGraph.rootExecContextId, v4.execContextGraph.parentExecContextId, v4.execContextGraph.graph);
        }
        return t;
    }

    private static void initVariables(ExecContextParamsYamlV4.VariableDeclarationV4 v4, ExecContextParamsYamlV5.VariableDeclarationV5 v) {
        v.inline.putAll(v4.inline);
        v.globals = v4.globals;
        v4.inputs.forEach(o->v.inputs.add(toVariable(o)));
        v4.outputs.forEach(o->v.outputs.add(toVariable(o)));
    }

    private static ExecContextParamsYamlV5.ProcessV5 toProcess(ExecContextParamsYamlV4.ProcessV4 p2) {
        ExecContextParamsYamlV5.ProcessV5 p = new ExecContextParamsYamlV5.ProcessV5();
        p.function = toFunction(p2.function);
        p.preFunctions = p2.preFunctions!=null ? p2.preFunctions.stream().map(ExecContextParamsYamlUtilsV4::toFunction).collect(Collectors.toList()) : null;
        p.postFunctions = p2.postFunctions!=null ? p2.postFunctions.stream().map(ExecContextParamsYamlUtilsV4::toFunction).collect(Collectors.toList()) : null;
        p2.inputs.stream().map(ExecContextParamsYamlUtilsV4::toVariable).collect(Collectors.toCollection(()->p.inputs));
        p2.outputs.stream().map(ExecContextParamsYamlUtilsV4::toVariable).collect(Collectors.toCollection(()->p.outputs));
        p.metas.addAll(p2.metas);
        if (p2.cache!=null) {
            p.cache = new ExecContextParamsYamlV5.CacheV5(p2.cache.enabled, p2.cache.omitInline, false);
        }
        p.processName = p2.processName;
        p.processCode = p2.processCode;
        p.internalContextId = p2.internalContextId;
        p.logic = p2.logic;
        p.timeoutBeforeTerminate = p2.timeoutBeforeTerminate;
        p.tag = p2.tags;
        p.priority = p2.priority;
        p.condition = p2.condition;
        return p;
    }

    private static ExecContextParamsYamlV5.VariableV5 toVariable(ExecContextParamsYamlV4.VariableV4 v) {
        return new ExecContextParamsYamlV5.VariableV5(v.name, v.context, v.sourcing, v.git, v.disk, v.parentContext, v.type, v.getNullable(), v.ext);
    }

    private static ExecContextParamsYamlV5.FunctionDefinitionV5 toFunction(ExecContextParamsYamlV4.FunctionDefinitionV4 f1) {
        return new ExecContextParamsYamlV5.FunctionDefinitionV5(f1.code, f1.params, f1.context, EnumsApi.FunctionRefType.code);
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
    }

    @Override
    public ExecContextParamsYamlUtilsV5 nextUtil() {
        return (ExecContextParamsYamlUtilsV5) ExecContextParamsYamlUtils.BASE_YAML_UTILS.getForVersion(5);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull ExecContextParamsYamlV4 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public ExecContextParamsYamlV4 to(@NonNull String s) {
        final ExecContextParamsYamlV4 p = getYaml().load(s);
        return p;
    }
}

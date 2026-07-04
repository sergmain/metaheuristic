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
public class ExecContextParamsYamlUtilsV6
        extends AbstractParamsYamlUtils<ExecContextParamsYamlV6, ExecContextParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 6;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ExecContextParamsYamlV6.class);
    }

    @NonNull
    @Override
    public ExecContextParamsYaml upgradeTo(@NonNull ExecContextParamsYamlV6 v6) {
        ExecContextParamsYaml t = new ExecContextParamsYaml();

        // right now we don't need to convert Graph because it has only one version of structure
        // so just copying of graph field is Ok
        t.clean = v6.clean;
        t.sourceCodeUid = v6.sourceCodeUid;
        t.desc = v6.desc;
        t.processesGraph = v6.processesGraph;
        v6.processes.stream().map(ExecContextParamsYamlUtilsV6::toProcess).collect(Collectors.toCollection(()->t.processes));
        initVariables(v6.variables, t.variables);
        if (v6.execContextGraph!=null) {
            t.execContextGraph = new ExecContextParamsYaml.ExecContextGraph(
                    v6.execContextGraph.rootExecContextId, v6.execContextGraph.parentExecContextId, v6.execContextGraph.graph);
        }
        t.columnNames.putAll(v6.columnNames);
        v6.groups.stream().map(ExecContextParamsYamlUtilsV6::toGroup).collect(Collectors.toCollection(()->t.groups));
        return t;
    }

    private static void initVariables(ExecContextParamsYamlV6.VariableDeclarationV6 v4, ExecContextParamsYaml.VariableDeclaration v) {
        v.inline.putAll(v4.inline);
        v.globals = v4.globals;
        v4.inputs.forEach(o->v.inputs.add(toVariable(o)));
        v4.outputs.forEach(o->v.outputs.add(toVariable(o)));
    }

    private static ExecContextParamsYaml.Process toProcess(ExecContextParamsYamlV6.ProcessV6 p2) {
        ExecContextParamsYaml.Process p = new ExecContextParamsYaml.Process();
        p.function = toFunction(p2.function);
        p.preFunctions = p2.preFunctions!=null ? p2.preFunctions.stream().map(ExecContextParamsYamlUtilsV6::toFunction).collect(Collectors.toList()) : null;
        p.postFunctions = p2.postFunctions!=null ? p2.postFunctions.stream().map(ExecContextParamsYamlUtilsV6::toFunction).collect(Collectors.toList()) : null;
        p2.inputs.stream().map(ExecContextParamsYamlUtilsV6::toVariable).collect(Collectors.toCollection(()->p.inputs));
        p2.outputs.stream().map(ExecContextParamsYamlUtilsV6::toVariable).collect(Collectors.toCollection(()->p.outputs));
        p.metas.addAll(p2.metas);
        if (p2.cache!=null) {
            p.cache = new ExecContextParamsYaml.Cache(p2.cache.enabled, p2.cache.omitInline, p2.cache.cacheMeta);
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

    private static ExecContextParamsYaml.Group toGroup(ExecContextParamsYamlV6.GroupV6 g2) {
        ExecContextParamsYaml.Group g = new ExecContextParamsYaml.Group();
        g.name = g2.name;
        g2.body.stream().map(ExecContextParamsYamlUtilsV6::toProcess).collect(Collectors.toCollection(()->g.body));
        g2.inputs.stream().map(ExecContextParamsYamlUtilsV6::toVariable).collect(Collectors.toCollection(()->g.inputs));
        g2.outputs.stream().map(ExecContextParamsYamlUtilsV6::toVariable).collect(Collectors.toCollection(()->g.outputs));
        g.internalContextId = g2.internalContextId;
        g.resetPointProcessCode = g2.resetPointProcessCode;
        return g;
    }

    private static ExecContextParamsYaml.Variable toVariable(ExecContextParamsYamlV6.VariableV6 v) {
        return new ExecContextParamsYaml.Variable(v.name, v.context, v.sourcing, v.git, v.disk, v.parentContext, v.type, v.getNullable(), v.ext, null);
    }

    private static ExecContextParamsYaml.FunctionDefinition toFunction(ExecContextParamsYamlV6.FunctionDefinitionV6 f1) {
        return new ExecContextParamsYaml.FunctionDefinition(f1.code, f1.params, f1.context, f1.refType);
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull ExecContextParamsYamlV6 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public ExecContextParamsYamlV6 to(@NonNull String s) {
        final ExecContextParamsYamlV6 p = getYaml().load(s);
        return p;
    }
}

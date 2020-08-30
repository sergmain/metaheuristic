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

package ai.metaheuristic.ai.yaml.exec_context;

import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYamlV1;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class ExecContextParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<ExecContextParamsYamlV1, ExecContextParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ExecContextParamsYamlV1.class);
    }

    @NonNull
    @Override
    public ExecContextParamsYaml upgradeTo(@NonNull ExecContextParamsYamlV1 v1, Long ... vars) {
        ExecContextParamsYaml t = new ExecContextParamsYaml();

        // right now we don't need to convert Graph because it has only one version of structure
        // so just copying of graph field is Ok
        t.clean = v1.clean;
        t.sourceCodeUid = v1.sourceCodeUid;
        t.graph = v1.graph;
        t.processesGraph = v1.processesGraph;
        v1.processes.stream().map(ExecContextParamsYamlUtilsV1::toProcess).collect(Collectors.toCollection(()->t.processes));
        initVariables(v1.variables, t.variables);

        return t;
    }

    private void initVariables(ExecContextParamsYamlV1.VariableDeclarationV1 v1, ExecContextParamsYaml.VariableDeclaration v) {
        v.inline.putAll(v1.inline);
        v.globals = v1.globals;
        v.startInputAs = v1.startInputAs;
    }

    private static ExecContextParamsYaml.Process toProcess(ExecContextParamsYamlV1.ProcessV1 p1) {
        ExecContextParamsYaml.Process p = new ExecContextParamsYaml.Process();
        BeanUtils.copyProperties(p1, p, "function", "preFunctions", "postFunctions", "inputs", "outputs", "metas");
        p.function = toFunction(p1.function);
        p.preFunctions = p1.preFunctions!=null ? p1.preFunctions.stream().map(ExecContextParamsYamlUtilsV1::toFunction).collect(Collectors.toList()) : null;
        p.postFunctions = p1.postFunctions!=null ? p1.postFunctions.stream().map(ExecContextParamsYamlUtilsV1::toFunction).collect(Collectors.toList()) : null;
        p1.inputs.stream().map(ExecContextParamsYamlUtilsV1::toVariable).collect(Collectors.toCollection(()->p.inputs));
        p1.outputs.stream().map(ExecContextParamsYamlUtilsV1::toVariable).collect(Collectors.toCollection(()->p.outputs));
        p.metas.addAll(p1.metas);
        return p;
    }

    private static ExecContextParamsYaml.Variable toVariable(ExecContextParamsYamlV1.VariableV1 v1) {
        return new ExecContextParamsYaml.Variable(v1.name, v1.context, v1.sourcing, v1.git, v1.disk, v1.parentContext, v1.type);
    }

    @NonNull
    private static ExecContextParamsYaml.FunctionDefinition toFunction(ExecContextParamsYamlV1.FunctionDefinitionV1 f1) {
        return new ExecContextParamsYaml.FunctionDefinition(f1.code, f1.params, f1.context);
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
    public String toString(@NonNull ExecContextParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public ExecContextParamsYamlV1 to(@NonNull String s) {
        final ExecContextParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}

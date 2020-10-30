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

import ai.metaheuristic.api.data.exec_context.ExecContextParamsYamlV1;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYamlV2;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class ExecContextParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<ExecContextParamsYamlV1, ExecContextParamsYamlV2, Void, Void, Void, Void> {

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
    public ExecContextParamsYamlV2 upgradeTo(@NonNull ExecContextParamsYamlV1 v1, @Nullable Long ... vars) {
        ExecContextParamsYamlV2 t = new ExecContextParamsYamlV2();

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

    private void initVariables(ExecContextParamsYamlV1.VariableDeclarationV1 v1, ExecContextParamsYamlV2.VariableDeclarationV2 v) {
        v.inline.putAll(v1.inline);
        v.globals = v1.globals;
        v.startInputAs = v1.startInputAs;
    }

    private static ExecContextParamsYamlV2.ProcessV2 toProcess(ExecContextParamsYamlV1.ProcessV1 p1) {
        ExecContextParamsYamlV2.ProcessV2 p2 = new ExecContextParamsYamlV2.ProcessV2();
        BeanUtils.copyProperties(p1, p2, "function", "preFunctions", "postFunctions", "inputs", "outputs", "metas");
        p2.function = toFunction(p1.function);
        p2.preFunctions = p1.preFunctions!=null ? p1.preFunctions.stream().map(ExecContextParamsYamlUtilsV1::toFunction).collect(Collectors.toList()) : null;
        p2.postFunctions = p1.postFunctions!=null ? p1.postFunctions.stream().map(ExecContextParamsYamlUtilsV1::toFunction).collect(Collectors.toList()) : null;
        p1.inputs.stream().map(ExecContextParamsYamlUtilsV1::toVariable).collect(Collectors.toCollection(()->p2.inputs));
        p1.outputs.stream().map(ExecContextParamsYamlUtilsV1::toVariable).collect(Collectors.toCollection(()->p2.outputs));
        p2.metas.addAll(p1.metas);
        p2.cache = null;
        return p2;
    }

    private static ExecContextParamsYamlV2.VariableV2 toVariable(ExecContextParamsYamlV1.VariableV1 v1) {
        return new ExecContextParamsYamlV2.VariableV2(v1.name, v1.context, v1.sourcing, v1.git, v1.disk, v1.parentContext, v1.type, v1.getNullable());
    }

    @NonNull
    private static ExecContextParamsYamlV2.FunctionDefinitionV2 toFunction(ExecContextParamsYamlV1.FunctionDefinitionV1 f1) {
        return new ExecContextParamsYamlV2.FunctionDefinitionV2(f1.code, f1.params, f1.context);
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

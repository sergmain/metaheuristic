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

import ai.metaheuristic.api.data.exec_context.ExecContextParamsYamlV2;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYamlV3;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 10/29/2020
 * Time: 11:38 PM
 */
public class ExecContextParamsYamlUtilsV2
        extends AbstractParamsYamlUtils<ExecContextParamsYamlV2, ExecContextParamsYamlV3, ExecContextParamsYamlUtilsV3,
        Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ExecContextParamsYamlV2.class);
    }

    @NonNull
    @Override
    public ExecContextParamsYamlV3 upgradeTo(@NonNull ExecContextParamsYamlV2 v1) {
        ExecContextParamsYamlV3 t = new ExecContextParamsYamlV3();

        // right now we don't need to convert Graph because it has only one version of structure
        // so just copying of graph field is Ok
        t.clean = v1.clean;
        t.sourceCodeUid = v1.sourceCodeUid;
        t.processesGraph = v1.processesGraph;
        v1.processes.stream().map(ExecContextParamsYamlUtilsV2::toProcess).collect(Collectors.toCollection(()->t.processes));
        initVariables(v1.variables, t.variables);

        return t;
    }

    private void initVariables(ExecContextParamsYamlV2.VariableDeclarationV2 v2, ExecContextParamsYamlV3.VariableDeclarationV3 v) {
        v.inline.putAll(v2.inline);
        v.globals = v2.globals;
        v.inputs.add(new ExecContextParamsYamlV3.VariableV3(v2.startInputAs));
    }

    private static ExecContextParamsYamlV3.ProcessV3 toProcess(ExecContextParamsYamlV2.ProcessV2 p2) {
        ExecContextParamsYamlV3.ProcessV3 p = new ExecContextParamsYamlV3.ProcessV3();
        BeanUtils.copyProperties(p2, p, "function", "preFunctions", "postFunctions", "inputs", "outputs", "metas");
        p.function = toFunction(p2.function);
        p.preFunctions = p2.preFunctions!=null ? p2.preFunctions.stream().map(ExecContextParamsYamlUtilsV2::toFunction).collect(Collectors.toList()) : null;
        p.postFunctions = p2.postFunctions!=null ? p2.postFunctions.stream().map(ExecContextParamsYamlUtilsV2::toFunction).collect(Collectors.toList()) : null;
        p2.inputs.stream().map(ExecContextParamsYamlUtilsV2::toVariable).collect(Collectors.toCollection(()->p.inputs));
        p2.outputs.stream().map(ExecContextParamsYamlUtilsV2::toVariable).collect(Collectors.toCollection(()->p.outputs));
        p.metas.addAll(p2.metas);
        if (p2.cache!=null) {
            p.cache = new ExecContextParamsYamlV3.CacheV3(p2.cache.enabled, p2.cache.omitInline);
        }
        p.tags = p2.tags;
        p.priority = p2.priority;
        return p;
    }

    private static ExecContextParamsYamlV3.VariableV3 toVariable(ExecContextParamsYamlV2.VariableV2 v) {
        return new ExecContextParamsYamlV3.VariableV3(v.name, v.context, v.sourcing, v.git, v.disk, v.parentContext, v.type, v.getNullable(), v.ext);
    }

    @NonNull
    private static ExecContextParamsYamlV3.FunctionDefinitionV3 toFunction(ExecContextParamsYamlV2.FunctionDefinitionV2 f1) {
        return new ExecContextParamsYamlV3.FunctionDefinitionV3(f1.code, f1.params, f1.context);
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
    }

    @Override
    public ExecContextParamsYamlUtilsV3 nextUtil() {
        return (ExecContextParamsYamlUtilsV3) ExecContextParamsYamlUtils.BASE_YAML_UTILS.getForVersion(3);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull ExecContextParamsYamlV2 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public ExecContextParamsYamlV2 to(@NonNull String s) {
        final ExecContextParamsYamlV2 p = getYaml().load(s);
        return p;
    }

}

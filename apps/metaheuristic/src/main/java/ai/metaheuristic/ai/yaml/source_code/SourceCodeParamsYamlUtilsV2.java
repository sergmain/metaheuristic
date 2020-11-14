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

package ai.metaheuristic.ai.yaml.source_code;

import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV2;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 10/24/2020
 * Time: 11:51 AM
 */
public class SourceCodeParamsYamlUtilsV2
        extends AbstractParamsYamlUtils<
        SourceCodeParamsYamlV2,
        SourceCodeParamsYaml,
        Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(SourceCodeParamsYamlV2.class);
    }

    @NonNull
    @Override
    public SourceCodeParamsYaml upgradeTo(@NonNull SourceCodeParamsYamlV2 v2, Long ... vars) {
        v2.checkIntegrity();

        SourceCodeParamsYaml p = new SourceCodeParamsYaml();
        p.source = new SourceCodeParamsYaml.SourceCodeYaml();
        if (v2.source.metas!=null){
            p.source.metas = v2.source.metas;
        }
        if (v2.source.variables!=null) {
            p.source.variables = new SourceCodeParamsYaml.VariableDefinition(v2.source.variables.globals, v2.source.variables.startInputAs);
            v2.source.variables.inline.forEach(p.source.variables.inline::put);
        }
        p.source.clean = v2.source.clean;
        p.source.processes = v2.source.processes.stream().map(SourceCodeParamsYamlUtilsV2::toProcess).collect(Collectors.toList());

        p.source.uid = v2.source.uid;
        if (v2.source.ac!=null) {
            p.source.ac = new SourceCodeParamsYaml.AccessControl(v2.source.ac.groups);
        }
        p.checkIntegrity();
        return p;
    }

    @NonNull
    private static SourceCodeParamsYaml.Process toProcess(SourceCodeParamsYamlV2.ProcessV2 o) {
        SourceCodeParamsYaml.Process pr = new SourceCodeParamsYaml.Process();
        pr.name = o.name;
        pr.code = o.code;
        pr.timeoutBeforeTerminate = o.timeoutBeforeTerminate;
        o.inputs.stream().map(v->new SourceCodeParamsYaml.Variable(v.sourcing, v.git, v.disk, v.name, v.parentContext, v.array, v.type, v.getNullable())).forEach(pr.inputs::add);
        o.outputs.stream().map(v->new SourceCodeParamsYaml.Variable(v.sourcing, v.git, v.disk, v.name, v.parentContext, v.array, v.type, v.getNullable())).forEach(pr.outputs::add);
        pr.function = new SourceCodeParamsYaml.FunctionDefForSourceCode(o.function.code, o.function.params, o.function.context);
        pr.preFunctions = o.preFunctions.stream().map(d->new SourceCodeParamsYaml.FunctionDefForSourceCode(d.code, d.params, d.context)).collect(Collectors.toList());
        pr.postFunctions = o.postFunctions.stream().map(d->new SourceCodeParamsYaml.FunctionDefForSourceCode(d.code, d.params, d.context)).collect(Collectors.toList());
        pr.metas = o.metas;
        if (o.cache!=null) {
            pr.cache = new SourceCodeParamsYaml.Cache(o.cache.enabled);
        }
        pr.tags = o.tags;
        pr.priority = o.priority;

        pr.subProcesses = o.subProcesses!=null
                ?  new SourceCodeParamsYaml.SubProcesses(
                o.subProcesses.logic, o.subProcesses.processes.stream().map(SourceCodeParamsYamlUtilsV2::toProcess).collect(Collectors.toList()) )
                : null;

        return pr;
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
    public String toString(@NonNull SourceCodeParamsYamlV2 sourceCodeParamsYaml) {
        return getYaml().dump(sourceCodeParamsYaml);
    }

    @NonNull
    @Override
    public SourceCodeParamsYamlV2 to(@NonNull String s) {
        final SourceCodeParamsYamlV2 p = getYaml().load(s);
        if (p.source ==null) {
            throw new IllegalStateException("#635.010 SourceCode Yaml is null");
        }
        return p;
    }


}

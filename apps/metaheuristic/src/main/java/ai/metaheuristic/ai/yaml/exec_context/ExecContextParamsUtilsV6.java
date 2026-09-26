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

import ai.metaheuristic.api.data.exec_context.ExecContextParams;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYamlV6;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;

import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 01/30/2022
 * Time: 2:13 PM
 */
@SuppressWarnings("DuplicatedCode")
public class ExecContextParamsUtilsV6
        extends AbstractParamsYamlUtils<ExecContextParamsYamlV6, ExecContextParams, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 6;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ExecContextParamsYamlV6.class);
    }

    @Override
    public ExecContextParams upgradeTo(ExecContextParamsYamlV6 v6) {
        ExecContextParams t = new ExecContextParams();

        // right now we don't need to convert Graph because it has only one version of structure
        // so just copying of graph field is Ok
        t.clean = v6.clean;
        t.sourceCodeUid = v6.sourceCodeUid;
        t.desc = v6.desc;
        t.processesGraph = v6.processesGraph;
        v6.processes.stream().map(ExecContextParamsUtilsV6::toProcess).collect(Collectors.toCollection(()->t.processes));
        initVariables(v6.variables, t.variables);
        if (v6.execContextGraph!=null) {
            t.execContextGraph = new ExecContextParams.ExecContextGraph(
                    v6.execContextGraph.rootExecContextId, v6.execContextGraph.parentExecContextId, v6.execContextGraph.graph);
        }
        t.columnNames.putAll(v6.columnNames);
        v6.groups.stream().map(ExecContextParamsUtilsV6::toGroup).collect(Collectors.toCollection(()->t.groups));
        t.gitSources = toGitSources(v6.gitSources);
        return t;
    }

    private static void initVariables(ExecContextParamsYamlV6.VariableDeclarationV6 v4, ExecContextParams.VariableDeclaration v) {
        v.inline.putAll(v4.inline);
        v.globals = v4.globals;
        v4.inputs.forEach(o->v.inputs.add(toVariable(o)));
        v4.outputs.forEach(o->v.outputs.add(toVariable(o)));
    }

    private static ExecContextParams.Process toProcess(ExecContextParamsYamlV6.ProcessV6 p2) {
        ExecContextParams.Process p = new ExecContextParams.Process();
        p.function = toFunction(p2.function);
        // pre/post Functions are not supported anymore - a V6 that still carries them loses them here
        p2.inputs.stream().map(ExecContextParamsUtilsV6::toVariable).collect(Collectors.toCollection(()->p.inputs));
        p2.outputs.stream().map(ExecContextParamsUtilsV6::toVariable).collect(Collectors.toCollection(()->p.outputs));
        p.metas.addAll(p2.metas);
        if (p2.cache!=null) {
            p.cache = new ExecContextParams.Cache(p2.cache.enabled, p2.cache.omitInline, p2.cache.cacheMeta);
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
        p.graft = toGraft(p2.graft);
        return p;
    }

    private static ExecContextParams.@Nullable DiskParams toDiskParams(ExecContextParamsYamlV6.@Nullable DiskParamsV6 disk) {
        return disk==null ? null : new ExecContextParams.DiskParams(disk.mask, disk.code, disk.path);
    }

    private static ExecContextParams.@Nullable GitParams toGitParams(ExecContextParamsYamlV6.@Nullable GitParamsV6 git) {
        return git==null ? null : new ExecContextParams.GitParams(git.repo, git.branch, git.commit, git.path);
    }

    private static ExecContextParams.@Nullable GitSources toGitSources(ExecContextParamsYamlV6.@Nullable GitSourcesV6 src) {
        if (src==null) {
            return null;
        }
        ExecContextParams.GitSources trg = new ExecContextParams.GitSources();
        for (ExecContextParamsYamlV6.GitSourceInfoV6 info : src.gitSourceInfos) {
            trg.gitSourceInfos.add(new ExecContextParams.GitSourceInfo(info.functionCode, toGitParams(info.git)));
        }
        return trg;
    }

    private static ExecContextParams.@Nullable Graft toGraft(ExecContextParamsYamlV6.@Nullable GraftV6 g2) {
        if (g2==null) {
            return null;
        }
        ExecContextParams.Graft g = new ExecContextParams.Graft(g2.groupName);
        g.inputBindings.addAll(g2.inputBindings);
        g.outputBindings.addAll(g2.outputBindings);
        g.driver = g2.driver;
        g.at = g2.at;
        return g;
    }

    private static ExecContextParams.Group toGroup(ExecContextParamsYamlV6.GroupV6 g2) {
        ExecContextParams.Group g = new ExecContextParams.Group();
        g.name = g2.name;
        g2.body.stream().map(ExecContextParamsUtilsV6::toProcess).collect(Collectors.toCollection(()->g.body));
        g2.inputs.stream().map(ExecContextParamsUtilsV6::toVariable).collect(Collectors.toCollection(()->g.inputs));
        g2.outputs.stream().map(ExecContextParamsUtilsV6::toVariable).collect(Collectors.toCollection(()->g.outputs));
        g.internalContextId = g2.internalContextId;
        g.resetPointProcessCode = g2.resetPointProcessCode;
        return g;
    }

    private static ExecContextParams.Variable toVariable(ExecContextParamsYamlV6.VariableV6 v) {
        return new ExecContextParams.Variable(v.name, v.context, v.sourcing, toGitParams(v.git), toDiskParams(v.disk), v.parentContext, v.type, v.getNullable(), v.ext, null);
    }

    private static ExecContextParams.FunctionDefinition toFunction(ExecContextParamsYamlV6.FunctionDefinitionV6 f1) {
        return new ExecContextParams.FunctionDefinition(f1.code, f1.params, f1.context, f1.refType);
    }

    @Override
    public Void downgradeTo(Void yaml) {
        return null;
    }

    @Override
    public @Nullable Void nextUtil() {
        return null;
    }

    @Override
    public @Nullable Void prevUtil() {
        return null;
    }

    @Override
    public String toString(ExecContextParamsYamlV6 yaml) {
        return getYaml().dump(yaml);
    }

    @Override
    public ExecContextParamsYamlV6 to(String s) {
        final ExecContextParamsYamlV6 p = getYaml().load(s);
        return p;
    }
}

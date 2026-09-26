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
import ai.metaheuristic.api.data.exec_context.ExecContextParamsV1;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.exceptions.ParamsProcessingException;
import ai.metaheuristic.commons.json.versioning_json.AbstractParamsJsonUtils;
import ai.metaheuristic.commons.json.versioning_json.BaseJsonUtils;
import tools.jackson.core.JacksonException;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.stream.Collectors;

/**
 * V1 of the ExecContext params JSON chain, and currently its head: it upgrades V1 straight to the
 * version-less {@link ExecContextParams}, so {@link #nextUtil()} ends the chain.
 *
 * <p>Error code prefix: {@code 01.901.} (unique to this class).
 *
 * @author Serge
 * Date: 01/30/2022
 * Time: 2:13 PM
 */
@SuppressWarnings("DuplicatedCode")
public class ExecContextParamsUtilsV1
        extends AbstractParamsJsonUtils<ExecContextParamsV1, ExecContextParams, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public ExecContextParams upgradeTo(@NonNull ExecContextParamsV1 v1) {
        ExecContextParams t = new ExecContextParams();

        // right now we don't need to convert Graph because it has only one version of structure
        // so just copying of graph field is Ok
        t.clean = v1.clean;
        t.sourceCodeUid = v1.sourceCodeUid;
        t.desc = v1.desc;
        t.processesGraph = v1.processesGraph;
        v1.processes.stream().map(ExecContextParamsUtilsV1::toProcess).collect(Collectors.toCollection(()->t.processes));
        initVariables(v1.variables, t.variables);
        if (v1.execContextGraph!=null) {
            t.execContextGraph = new ExecContextParams.ExecContextGraph(
                    v1.execContextGraph.rootExecContextId, v1.execContextGraph.parentExecContextId, v1.execContextGraph.graph);
        }
        t.columnNames.putAll(v1.columnNames);
        v1.groups.stream().map(ExecContextParamsUtilsV1::toGroup).collect(Collectors.toCollection(()->t.groups));
        t.gitSources = toGitSources(v1.gitSources);
        return t;
    }

    private static void initVariables(ExecContextParamsV1.VariableDeclarationV1 v4, ExecContextParams.VariableDeclaration v) {
        v.inline.putAll(v4.inline);
        v.globals = v4.globals;
        v4.inputs.forEach(o->v.inputs.add(toVariable(o)));
        v4.outputs.forEach(o->v.outputs.add(toVariable(o)));
    }

    private static ExecContextParams.Process toProcess(ExecContextParamsV1.ProcessV1 p2) {
        ExecContextParams.Process p = new ExecContextParams.Process();
        p.function = toFunction(p2.function);
        // pre/post Functions are not supported anymore - a V6 that still carries them loses them here
        p2.inputs.stream().map(ExecContextParamsUtilsV1::toVariable).collect(Collectors.toCollection(()->p.inputs));
        p2.outputs.stream().map(ExecContextParamsUtilsV1::toVariable).collect(Collectors.toCollection(()->p.outputs));
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

    private static ExecContextParams.@Nullable DiskParams toDiskParams(ExecContextParamsV1.@Nullable DiskParamsV1 disk) {
        return disk==null ? null : new ExecContextParams.DiskParams(disk.mask, disk.code, disk.path);
    }

    private static ExecContextParams.@Nullable GitParams toGitParams(ExecContextParamsV1.@Nullable GitParamsV1 git) {
        return git==null ? null : new ExecContextParams.GitParams(git.repo, git.branch, git.commit, git.path);
    }

    private static ExecContextParams.@Nullable GitSources toGitSources(ExecContextParamsV1.@Nullable GitSourcesV1 src) {
        if (src==null) {
            return null;
        }
        ExecContextParams.GitSources trg = new ExecContextParams.GitSources();
        for (ExecContextParamsV1.GitSourceInfoV1 info : src.gitSourceInfos) {
            trg.gitSourceInfos.add(new ExecContextParams.GitSourceInfo(info.functionCode, toGitParams(info.git)));
        }
        return trg;
    }

    private static ExecContextParams.@Nullable Graft toGraft(ExecContextParamsV1.@Nullable GraftV1 g2) {
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

    private static ExecContextParams.Group toGroup(ExecContextParamsV1.GroupV1 g2) {
        ExecContextParams.Group g = new ExecContextParams.Group();
        g.name = g2.name;
        g2.body.stream().map(ExecContextParamsUtilsV1::toProcess).collect(Collectors.toCollection(()->g.body));
        g2.inputs.stream().map(ExecContextParamsUtilsV1::toVariable).collect(Collectors.toCollection(()->g.inputs));
        g2.outputs.stream().map(ExecContextParamsUtilsV1::toVariable).collect(Collectors.toCollection(()->g.outputs));
        g.internalContextId = g2.internalContextId;
        g.resetPointProcessCode = g2.resetPointProcessCode;
        return g;
    }

    private static ExecContextParams.Variable toVariable(ExecContextParamsV1.VariableV1 v) {
        return new ExecContextParams.Variable(v.name, v.context, v.sourcing, toGitParams(v.git), toDiskParams(v.disk), v.parentContext, v.type, v.getNullable(), v.ext, null);
    }

    private static ExecContextParams.FunctionDefinition toFunction(ExecContextParamsV1.FunctionDefinitionV1 f1) {
        return new ExecContextParams.FunctionDefinition(f1.code, f1.params, f1.context, f1.refType);
    }

    @Override
    public Void downgradeTo(@NonNull Void unused) {
        throw new DowngradeNotSupportedException();
    }

    @Override
    public @Nullable Void nextUtil() {
        return null;
    }

    @Override
    public @Nullable Void prevUtil() {
        return null;
    }

    @NonNull
    @Override
    public String toString(@NonNull ExecContextParamsV1 json) {
        try {
            return BaseJsonUtils.getMapper().writeValueAsString(json);
        }
        catch (JacksonException e) {
            throw new ParamsProcessingException("01.901.040 Error writing ExecContextParamsV1: " + e.getMessage(), e);
        }
    }

    @NonNull
    @Override
    public ExecContextParamsV1 to(@NonNull String s) {
        try {
            return BaseJsonUtils.getMapper().readValue(s, ExecContextParamsV1.class);
        }
        catch (JacksonException e) {
            throw new ParamsProcessingException("01.901.020 Error reading ExecContextParamsV1: " + e.getMessage(), e);
        }
    }
}

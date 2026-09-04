/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.commons.utils;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Finds the git-sourced external Functions in a DAG and pins each one to a concrete revision.
 *
 * <p>Collaborators arrive as function parameters rather than injected fields, so the whole of this
 * runs with no Spring context and no DB: the caller passes a real Function lookup and a real
 * `ls-remote` resolver, a test passes a Map and a constant.
 *
 * <p>Error code prefix: {@code 01.921.} (unique to this class).
 *
 * @author Sergio Lissner
 * Date: 9/3/2026
 * Time: 3:10 PM
 */
@Slf4j
public class ExecContextGitSourceUtils {

    /**
     * Every external Function code the DAG can reach, in encounter order and de-duplicated.
     *
     * <p>Covers pre/post Functions as well as the main one, and reaches into `groups[].body` because
     * DSL v2 keeps a grafted group's processes there rather than in the top-level list - a git-sourced
     * Function inside a group body still executes and still needs pinning.
     */
    public static List<String> collectExternalFunctionCodes(
            List<ExecContextParamsYaml.Process> processes, List<ExecContextParamsYaml.Group> groups) {

        final Set<String> codes = new LinkedHashSet<>();
        for (ExecContextParamsYaml.Process p : processes) {
            collectFromProcess(p, codes);
        }
        for (ExecContextParamsYaml.Group g : groups) {
            for (ExecContextParamsYaml.Process p : g.body) {
                collectFromProcess(p, codes);
            }
        }
        return List.copyOf(codes);
    }

    private static void collectFromProcess(ExecContextParamsYaml.Process p, Set<String> codes) {
        addIfExternal(p.function, codes);
        if (p.preFunctions!=null) {
            for (ExecContextParamsYaml.FunctionDefinition fd : p.preFunctions) {
                addIfExternal(fd, codes);
            }
        }
        if (p.postFunctions!=null) {
            for (ExecContextParamsYaml.FunctionDefinition fd : p.postFunctions) {
                addIfExternal(fd, codes);
            }
        }
    }

    private static void addIfExternal(ExecContextParamsYaml.@Nullable FunctionDefinition fd, Set<String> codes) {
        if (fd==null || fd.code==null) {
            return;
        }
        // an internal function is a Spring bean in the Dispatcher, it has no sourcing and nothing to pin
        if (fd.context!=EnumsApi.FunctionExecContext.external) {
            return;
        }
        codes.add(fd.code);
    }

    /**
     * Pins each git-sourced Function among `functionCodes` to a concrete revision.
     *
     * @param configResolver code -> descriptor; returns null for a code with no registered Function.
     *                       Missing Functions are NOT an error here - SourceCode validation owns that
     *                       verdict and reports it with better context.
     * @param headResolver   resolves a branch tip to a sha; returns null when the repo or branch is
     *                       unreachable. Only called for descriptors that actually name HEAD.
     * @return the pinned revisions, or null when the DAG uses no git-sourced Function - the common case,
     *         and null keeps it out of the stored YAML entirely.
     */
    public static ExecContextParamsYaml.@Nullable GitSources resolveGitSources(
            List<String> functionCodes,
            Function<String, FunctionConfigYaml> configResolver,
            Function<GitInfo, String> headResolver) {

        final List<ExecContextParamsYaml.GitSourceInfo> infos = new ArrayList<>();
        for (String code : functionCodes) {
            final FunctionConfigYaml cfg = configResolver.apply(code);
            if (cfg==null) {
                continue;
            }
            if (cfg.function.sourcing!=EnumsApi.FunctionSourcing.git) {
                continue;
            }
            final GitInfo git = cfg.function.git;
            if (git==null) {
                // FunctionCoreUtils.validate() already rejects this at registration; belt and braces
                log.warn("01.921.010 Function {} has sourcing==git but no GitInfo, skipped", code);
                continue;
            }
            final String commit = resolveCommit(code, git, headResolver);
            if (commit==null) {
                throw new IllegalStateException(
                    "01.921.020 Can't resolve a git revision for Function " + code + ", repo: " + git.repo + ", branch: " + git.branch);
            }
            // GitInfo (the Function descriptor's own type) is copied into GitParams (the ExecContext's own
            // type) here, at the boundary - the pin is not a reference to the descriptor's field
            infos.add(new ExecContextParamsYaml.GitSourceInfo(code,
                new ExecContextParamsYaml.GitParams(git.repo, git.branch, commit, git.path)));
            // NOTE: built field-by-field rather than via GitParams.from(git) because `commit` is the
            // RESOLVED sha, not the descriptor's value
        }
        if (infos.isEmpty()) {
            return null;
        }
        final ExecContextParamsYaml.GitSources gitSources = new ExecContextParamsYaml.GitSources();
        gitSources.gitSourceInfos.addAll(infos);
        return gitSources;
    }

    @Nullable
    private static String resolveCommit(String code, GitInfo git, Function<GitInfo, String> headResolver) {
        if (!GtiUtils.isHeadRevision(git.commit)) {
            // already a concrete revision - the descriptor pinned it, nothing to look up
            return git.commit;
        }
        final String sha = headResolver.apply(git);
        log.info("01.921.030 Function {} resolved to {} from {}#{}", code, sha, git.repo, git.branch);
        return sha;
    }
}

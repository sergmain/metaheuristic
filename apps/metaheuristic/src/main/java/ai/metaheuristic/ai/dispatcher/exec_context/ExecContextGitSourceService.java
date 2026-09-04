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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.api.data.GitData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.utils.ExecContextGitSourceUtils;
import ai.metaheuristic.commons.utils.GtiUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Supplies the two real collaborators - a Function lookup and an `ls-remote` resolver - to the pure
 * DAG walk in {@link ExecContextGitSourceUtils}.
 *
 * <p>Error code prefix: {@code 01.922.} (unique to this class).
 *
 * @author Sergio Lissner
 * Date: 9/3/2026
 * Time: 3:25 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecContextGitSourceService {

    // an unreachable host must not hold up ExecContext creation indefinitely
    private static final GitData.GitContext LS_REMOTE_CONTEXT = new GitData.GitContext(30L, 100);

    private final FunctionService functionService;

    public ExecContextParamsYaml.@Nullable GitSources resolveGitSources(
            List<ExecContextParamsYaml.Process> processes, List<ExecContextParamsYaml.Group> groups) {

        final List<String> codes = ExecContextGitSourceUtils.collectExternalFunctionCodes(processes, groups);
        if (codes.isEmpty()) {
            return null;
        }
        return ExecContextGitSourceUtils.resolveGitSources(codes, this::findConfig, ExecContextGitSourceService::resolveHead);
    }

    @Nullable
    private FunctionConfigYaml findConfig(String functionCode) {
        final Function function = functionService.findByCode(functionCode);
        return function==null ? null : function.getFunctionConfigYaml();
    }

    @Nullable
    private static String resolveHead(GitInfo git) {
        return GtiUtils.resolveHeadCommit(git.repo, git.branch, LS_REMOTE_CONTEXT);
    }
}

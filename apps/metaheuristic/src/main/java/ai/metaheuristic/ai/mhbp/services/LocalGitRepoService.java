/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.ai.mhbp.services;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.commons.system.SystemProcessLauncher;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.utils.StrUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

import static ai.metaheuristic.api.data.GitData.GitContext;
import static ai.metaheuristic.ai.mhbp.services.LocalGitSourcingService.prepareRepo;

/**
 * @author Sergio Lissner
 * Date: 4/14/2023
 * Time: 10:00 PM
 */
@Service
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class LocalGitRepoService {

    private final Globals globals;
    private Path gitPath;

    @PostConstruct
    public void postConstruct() {
        gitPath = globals.getHome().resolve("git");
    }

    public SystemProcessLauncher.ExecResult initGitRepo(GitInfo git) {
        return initGitRepo(git, gitPath, new GitContext(60L, globals.mhbp.max.consoleOutputLines));
    }

    @SneakyThrows
    public static SystemProcessLauncher.ExecResult initGitRepo(GitInfo git, Path gitPath, GitContext gitContext) {
        String url = git.getRepo();
        String code = StrUtils.asCode(url);
        Path p = gitPath.resolve(code);
        if (Files.notExists(p)) {
            Files.createDirectories(p);
        }
        SystemProcessLauncher.ExecResult execResult = prepareRepo(p, git, gitContext);
        return execResult;
    }
}

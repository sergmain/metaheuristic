/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

import ai.metaheuristic.ai.core.SystemProcessLauncher;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.mhbp.data.KbData;
import ai.metaheuristic.ai.processor.processor_environment.MetadataParams;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;

import static ai.metaheuristic.ai.mhbp.services.LocalGitSourcingService.*;

/**
 * @author Sergio Lissner
 * Date: 4/14/2023
 * Time: 10:00 PM
 */
@Service
@RequiredArgsConstructor
@Profile("dispatcher")
public class LocalGitRepoService {

    public final Globals globals;
    public final LocalGitSourcingService gitSourcingService;

    private Path gitPath;

    @PostConstruct
    public void postConstruct() {
        gitPath = globals.getHome().resolve("git");
    }

    public SystemProcessLauncher.ExecResult initGitRepo(KbData.KbGit git) {
        return initGitRepo(git, gitPath, new GitContext(60L, globals.mhbp.max.consoleOutputLines));
    }

    @SneakyThrows
    public static SystemProcessLauncher.ExecResult initGitRepo(KbData.KbGit git, Path gitPath, GitContext gitContext) {
        String url = git.getRepo();
        String code = MetadataParams.asCode(url);
        Path p = gitPath.resolve(code);
        if (Files.notExists(p)) {
            Files.createDirectories(p);
        }
        SystemProcessLauncher.ExecResult execResult = prepareRepo(p.toFile(), git, gitContext);
        return execResult;
    }
}

/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.station.sourcing.git;

import aiai.ai.Enums;
import aiai.ai.core.ExecProcessService;
import aiai.ai.resource.AssetFile;
import aiai.ai.station.env.EnvService;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.data.SnippetApiData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Service
@Slf4j
public class GitSourcingService {

    public static final List<String> GIT_VERSION_CMD = List.of("git", "--version");
    public static final String GIT_VERSION_PREFIX = "git version";
    public static final String GIT_PREFIX = "git";

    private final ExecProcessService execProcessService;
    private final EnvService envService;

    public final GitStatusInfo gitStatusInfo;

    public GitSourcingService(ExecProcessService execProcessService, EnvService envService) {
        this.execProcessService = execProcessService;
        this.envService = envService;
        this.gitStatusInfo = getGitStatus();
    }

    @Data
    @AllArgsConstructor
    @ToString
    public static class GitExecResult {
        public File snippetDir;
        public SnippetApiData.SnippetExecResult snippetExecResult;
        public boolean isError;
        public String error;

        public GitExecResult(SnippetApiData.SnippetExecResult snippetExecResult, boolean isError, String error) {
            this.snippetExecResult = snippetExecResult;
            this.isError = isError;
            this.error = error;
        }
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GitStatusInfo {
        public Enums.GitStatus status;
        public String version;
        public String error;

        public GitStatusInfo(Enums.GitStatus status) {
            this.status = status;
        }
    }

    public GitStatusInfo getGitStatus() {
        GitExecResult result = execGitCmd(GIT_VERSION_CMD, 30L);
        if (result.isError) {
            return new GitStatusInfo(Enums.GitStatus.error, null, "Error: " + result.error);
        }

        if (result.snippetExecResult.exitCode!=0) {
            return new GitStatusInfo(
                    Enums.GitStatus.not_found, null,
                    "Console: " + result.snippetExecResult.console);
        }
        return new GitStatusInfo(Enums.GitStatus.installed, getGitVersion(result.snippetExecResult.console.toLowerCase()), null);
    }

    private GitExecResult execGitCmd(List<String> gitVersionCmd, long timeout) {
        try {
            File consoleLogFile = File.createTempFile("console-", ".log");
            consoleLogFile.deleteOnExit();
            SnippetApiData.SnippetExecResult snippetExecResult = execProcessService.execCommand(gitVersionCmd, new File("."), consoleLogFile, timeout);
            return new GitExecResult(snippetExecResult, false, null);
        } catch (InterruptedException | IOException e) {
            log.error("Error", e);
            return new GitExecResult(null, true, "Error: " + e.getMessage());
        }
    }

    private static AssetFile prepareSnippetDir(final File snippetRootDir, String snippetCode) {
        final AssetFile assetFile = new AssetFile();
        final File trgDir = new File(snippetRootDir, EnumsApi.BinaryDataType.SNIPPET.toString());
        log.info("Target dir: {}, exist: {}", trgDir.getAbsolutePath(), trgDir.exists() );
        if (!trgDir.exists() && !trgDir.mkdirs()) {
            assetFile.isError = true;
            log.error("#027.05 Can't create snippet dir: {}", trgDir.getAbsolutePath());
            return assetFile;
        }
        final String resId = snippetCode.replace(':', '_');
        final File resDir = new File(trgDir, resId);
        log.info("Resource dir: {}, exist: {}", resDir.getAbsolutePath(), resDir.exists() );
        if (!resDir.exists() && !resDir.mkdirs()) {
            assetFile.isError = true;
            log.error("#027.08 Can't create resource dir: {}", resDir.getAbsolutePath());
            return assetFile;
        }
        assetFile.file = resDir;
        return assetFile;
    }

    public GitExecResult prepareSnippet(final File snippetRootDir, SnippetApiData.SnippetConfig snippet) {

        log.info("#027.15 Start preparing snippet dir");
        AssetFile assetFile = prepareSnippetDir(snippetRootDir, snippet.code);
        log.info("#027.18 assetFile.isError: {}" , assetFile.isError);
        if (assetFile.isError) {
            return new GitExecResult(null,true, "Can't create dir for snippet " + snippet.code);
        }

        File snippetDir = assetFile.file;
        File repoDir = new File(snippetDir, "git");
        log.info("#027.22 Target dir: {}, exist: {}", repoDir.getAbsolutePath(), repoDir.exists() );

        if (!repoDir.exists()) {
            GitExecResult result = execClone(snippetDir, snippet);
            log.info("#027.25 Result of cloning repo: {}", result.toString());
            if (result.isError || !result.snippetExecResult.isOk()) {
                result = tryToRepairRepo(snippetDir, snippet);
                log.info("#027.28 Result of repairing of repo: {}", result.toString());
                return result;
            }
        }
        GitExecResult result = execRevParse(repoDir);
        log.info("#027.31 Result of execRevParse: {}", result.toString());
        if (result.isError) {
            return result;
        }
        if (!result.snippetExecResult.isOk) {
            return new GitExecResult(null,true, result.snippetExecResult.console);
        }
        if (!"true".equals(result.snippetExecResult.console.strip())) {
            result = tryToRepairRepo(repoDir, snippet);
            log.info("#027.34 Result of tryToRepairRepo: {}", result.toString());
            if (result.isError) {
                return result;
            }
            if (!result.snippetExecResult.isOk) {
                return new GitExecResult(null,true, result.snippetExecResult.console);
            }
        }
        result = execPullOrigin(repoDir, snippet);
        log.info("#027.38 Result of execPullOrigin: {}", result.toString());
        if (result.isError) {
            return result;
        }
        if (!result.snippetExecResult.isOk) {
            return new GitExecResult(null,true, result.snippetExecResult.console);
        }

        result = execCheckoutRevision(repoDir, snippet);
        log.info("#027.42 Result of execCheckoutRevision: {}", result.toString());
        if (result.isError) {
            return result;
        }
        if (!result.snippetExecResult.isOk) {
            return new GitExecResult(null,true, result.snippetExecResult.console);
        }
        log.info("#027.50 repoDir: {}, exist: {}", repoDir.getAbsolutePath(), repoDir.exists());

        return new GitExecResult(repoDir, new SnippetApiData.SnippetExecResult(true, 0, "" ), false, null);
    }

    private GitExecResult execCheckoutRevision(File repoDir, SnippetApiData.SnippetConfig snippet) {
        // git checkout sha1
        List<String> cmd = List.of("git", "-C", repoDir.getAbsolutePath(), "checkout", snippet.git.commit);
        log.info("exec {}", cmd);
        GitExecResult result = execGitCmd(cmd, 0L);
        return result;
    }

    private GitExecResult execPullOrigin(File repoDir, SnippetApiData.SnippetConfig snippet) {
        // pull origin master
        List<String> cmd = List.of("git", "-C", repoDir.getAbsolutePath(), "pull", "origin", snippet.git.branch);
        log.info("exec {}", cmd);
        GitExecResult result = execGitCmd(cmd, 0L);
        return result;
    }

    public GitExecResult tryToRepairRepo(File snippetDir, SnippetApiData.SnippetConfig snippet) {
        File repoDir = new File(snippetDir, "git");
        GitExecResult result;
        FileUtils.deleteQuietly(repoDir);
        if (repoDir.exists()) {
            return new GitExecResult(null,
                    true,
                    "Snippet "+snippet.code+", can't prepare repo dir: " + repoDir.getAbsolutePath());
        }
        result = execClone(snippetDir, snippet);
        return result;
    }

    private GitExecResult execRevParse(File repoDir) {
        // git rev-parse --is-inside-work-tree
        List<String> cmd = List.of("git", "-C", repoDir.getAbsolutePath(), "rev-parse", "--is-inside-work-tree");
        log.info("exec {}", cmd);
        GitExecResult result = execGitCmd(cmd, 60L);
        return result;
    }

    // TODO 2019-05-11 add this before checkout for new changes
    private GitExecResult execResetHardHead(File repoDir) {
        // git reset --hard HEAD
        List<String> cmd = List.of("git", "-C", repoDir.getAbsolutePath(), "reset", "--hard", "HEAD");
        log.info("exec {}", cmd);
        GitExecResult result = execGitCmd(cmd, 60L);
        return result;
    }

    private GitExecResult execClone(File repoDir, SnippetApiData.SnippetConfig snippet) {
        // git -C <path> clone <git-repo-url> git

        String mirror = envService.getEnvYaml().mirrors.get(snippet.git.repo);
        String gitUrl = mirror!=null ? mirror : snippet.git.repo;
        List<String> cmd = List.of("git", "-C", repoDir.getAbsolutePath(), "clone", gitUrl, "git");
        log.info("exec {}", cmd);
        //noinspection UnnecessaryLocalVariable
        GitExecResult result = execGitCmd(cmd, 0L);
        return result;
    }

    private static String getGitVersion(String s) {
        if (s.startsWith(GIT_VERSION_PREFIX)) {
            return s.substring(GIT_VERSION_PREFIX.length()).strip();
        }
        if (s.startsWith(GIT_PREFIX)) {
            return s.substring(GIT_PREFIX.length()).strip();
        }
        return StringUtils.substring(s, 0, 100);
    }
}

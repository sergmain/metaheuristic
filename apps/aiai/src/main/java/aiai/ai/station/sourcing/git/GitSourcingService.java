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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@Slf4j
public class GitSourcingService {

    public static final List<String> GIT_VERSION_CMD = List.of("git", "--version");
    public static final String GIT_VERSION_PREFIX = "git version";
    public static final String GIT_PREFIX = "git";

    private final ExecProcessService execProcessService;

    public final GitStatusInfo gitStatusInfo;

    public GitSourcingService(ExecProcessService execProcessService) {
        this.execProcessService = execProcessService;
        this.gitStatusInfo = getGitStatus();
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
        try {
            File consoleLogFile = File.createTempFile("console-", ".log");
            consoleLogFile.deleteOnExit();
            ExecProcessService.Result result = execProcessService.execCommand(GIT_VERSION_CMD, new File("."), consoleLogFile, 30L);
            final String console = FileUtils.readFileToString(consoleLogFile, StandardCharsets.UTF_8);
            if (result.exitCode!=0) {
                return new GitStatusInfo(
                        Enums.GitStatus.not_found, null,
                        "Console: " + console);
            }
            return new GitStatusInfo(Enums.GitStatus.installed, getGitVersion(console.toLowerCase()), null);
        } catch (InterruptedException | IOException e) {
            log.error("Error", e);
            return new GitStatusInfo(Enums.GitStatus.error, null, "Error: " + e.getMessage());
        }
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

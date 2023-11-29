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

package ai.metaheuristic.api.data;

import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BundleProcessingException;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.springframework.lang.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.List;

/**
 * @author Serge
 * Date: 7/26/2021
 * Time: 10:51 PM
 */
public class BundleData {

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class UploadingStatus extends BaseDataClass {

        public UploadingStatus(String errorMessage) {
            addErrorMessage(errorMessage);
        }

        public UploadingStatus(List<String> errorMessages) {
            addErrorMessages(errorMessages);
        }

        @JsonCreator
        public UploadingStatus (
            @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
            @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }
    }

    public static final class Cfg {
        @Nullable
        public final PrivateKey privateKey;
        // a path where all processing will be done
        public final Path baseDir;
        @Nullable
        public final GitInfo gitInfo;

        // a path where bundle will be created
        public Path workingDir;
        // a path where git repo was checked out
        public Path repoDir;
        // a path where bundle.yaml is located
        public Path currDir;

        public boolean inited = false;

        public Cfg(@Nullable PrivateKey privateKey, Path baseDir, @Nullable GitInfo gitInfo) {
            this.privateKey = privateKey;
            this.baseDir = baseDir;
            this.gitInfo = gitInfo;
        }

        @SneakyThrows
        public void initOtherPaths(@Nullable Path pathForBundles) {
            if (inited) {
                throw new IllegalStateException("paths were already inited");
            }
            if (pathForBundles==null) {
                Path tempDir = baseDir.resolve("bundles");
                Files.createDirectories(tempDir);
                Path workingDir = DirUtils.createTempPath(tempDir, "bundle-");
                if (workingDir == null) {
                    throw new BundleProcessingException("Can't create temp directory in path " + tempDir);
                }
                this.workingDir = workingDir;
            }
            else {
                this.workingDir = pathForBundles;
            }

            if (gitInfo != null) {
                if (repoDir == null) {
                    throw new BundleProcessingException("repoDir is null");
                }
                if (!S.b(gitInfo.path)) {
                    currDir = repoDir.resolve(gitInfo.path);
                } else {
                    currDir = repoDir;
                }
            } else {
                currDir = baseDir;
            }
        }
    }

    public record FunctionConfigAndFile(FunctionConfigYaml config, Path file) {}

    @AllArgsConstructor
    public static class BundleLocation {
        public final Path dir;
        public final Path bundleZipFile;
    }
}

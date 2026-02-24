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

package ai.metaheuristic.ai.utils;

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.commons.utils.ErrorUtils;
import ai.metaheuristic.commons.utils.FileSystemUtils;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.env.EnvParamsYaml;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 1/27/2021
 * Time: 1:09 AM
 */
@Slf4j
public class EnvServiceUtils {
    @Data
    @AllArgsConstructor
    public static class EnvYamlShort {
        public final Map<String, String> envs = new HashMap<>();
        public final List<EnvParamsYaml.DiskStorage> disk;

        public EnvYamlShort(EnvParamsYaml envYaml) {
            envYaml.envs.forEach(o->envs.put(o.code, o.exec));
            this.disk = envYaml.disk;
        }
    }

    private static Yaml getYamlForEnvYamlShort() {
        return YamlUtils.init(EnvYamlShort.class);
    }

    private static String envYamlShortToString(EnvYamlShort envYamlShort) {
        return YamlUtils.toString(envYamlShort, getYamlForEnvYamlShort());
    }

    @Nullable
    public static String prepareEnvironment(Path artifactDir, EnvYamlShort envYaml) {
        if (Files.notExists(artifactDir)) {
            try {
                Files.createDirectories(artifactDir);
            }
            catch (IOException e) {
                return "712.020 An error while creating a path "+ artifactDir.toAbsolutePath();
            }
        }
        Path envFile = artifactDir.resolve(ConstsApi.MH_ENV_FILE);
        if (Files.isDirectory(envFile)) {
            return "712.040 A path "+ artifactDir.toAbsolutePath()+" is dir, can't continue processing";
        }
        final String newEnv = envYamlShortToString(envYaml);

        try {
            FileSystemUtils.writeStringToFileWithSync(envFile, newEnv, StandardCharsets.UTF_8);
        } catch (IOException e) {
            final String es = "712.060 An error while creating " + ConstsApi.MH_ENV_FILE + ", error: " + ErrorUtils.getAllMessages(e);
            log.error(es, e);
            return es;
        }

        return null;
    }
}

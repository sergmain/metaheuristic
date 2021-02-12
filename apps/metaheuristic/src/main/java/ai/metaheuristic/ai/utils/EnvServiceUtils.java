/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.env.EnvParamsYaml;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.lang.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
        public final Map<String, String> envs;
        public final List<EnvParamsYaml.DiskStorage> disk;

        public EnvYamlShort(EnvParamsYaml envYaml) {
            this.envs = envYaml.envs;
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
    public static String prepareEnvironment(File artifactDir, EnvYamlShort envYaml) {
        if (!artifactDir.exists()) {
            if (!artifactDir.mkdirs()) {
                return "#712.020 An error while creating a path "+ artifactDir.getAbsolutePath();
            }
        }
        File envFile = new File(artifactDir, ConstsApi.MH_ENV_FILE);
        if (envFile.isDirectory()) {
            return "#712.040 A path "+ artifactDir.getAbsolutePath()+" is dir, can't continue processing";
        }
        final String newEnv = envYamlShortToString(envYaml);

        try {
            FileUtils.writeStringToFile(envFile, newEnv, StandardCharsets.UTF_8);
        } catch (IOException e) {
            final String es = "#712.060 An error while creating " + ConstsApi.MH_ENV_FILE + ", error: " + ErrorUtils.getAllMessages(e);
            log.error(es, e);
            return es;
        }

        return null;
    }
}

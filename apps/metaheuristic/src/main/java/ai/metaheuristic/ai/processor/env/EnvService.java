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

package ai.metaheuristic.ai.processor.env;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.commons.yaml.env.EnvParamsYaml;
import ai.metaheuristic.commons.yaml.env.EnvParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile("processor")
public class EnvService {

    private final Globals globals;

    private EnvParamsYaml envYaml;

    @PostConstruct
    public void init() {
        if (!globals.processor.enabled) {
            return;
        }

        final File envYamlFile = new File(globals.processor.dir.dir, Consts.ENV_YAML_FILE_NAME);
        if (!envYamlFile.exists()) {
            if (globals.processor.defaultEnvYamlFile == null) {
                log.warn("#747.020 Processor's env.yaml config file doesn't exist: {}", envYamlFile.getPath());
                throw new IllegalStateException("#747.012 Processor isn't configured, env.yaml is empty or doesn't exist");
            }
            if (!globals.processor.defaultEnvYamlFile.exists()) {
                log.warn("#747.030 Processor's default yaml.yaml file doesn't exist: {}", globals.processor.defaultEnvYamlFile.getAbsolutePath());
                throw new IllegalStateException("#747.014 Processor isn't configured, env.yaml is empty or doesn't exist");
            }
            try {
                FileUtils.copyFile(globals.processor.defaultEnvYamlFile, envYamlFile);
            } catch (IOException e) {
                log.error("#747.035 Error", e);
                throw new IllegalStateException("#747.040 Error while copying "+ globals.processor.defaultEnvYamlFile.getAbsolutePath()+" to " + envYamlFile.getAbsolutePath(), e);
            }
        }

        String env;
        try {
            env = FileUtils.readFileToString(envYamlFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("#747.045 Error", e);
            throw new IllegalStateException("#747.050 Error while reading file: " + envYamlFile.getAbsolutePath(), e);
        }

        envYaml = EnvParamsYamlUtils.BASE_YAML_UTILS.to(env);
        if (envYaml==null) {
            log.error("#747.060 env.yaml wasn't found or empty. path: {}{}env.yaml", globals.processor.dir.dir, File.separatorChar );
            throw new IllegalStateException("#747.062 Processor isn't configured, env.yaml is empty or doesn't exist");
        }
    }

    public EnvParamsYaml getEnvParamsYaml() {
        return envYaml;
    }

    @Nullable
    public String getTags(String processorCode) {
        synchronized (this) {
            return envYaml.processors.stream().filter(o->o.code.equals(processorCode)).findFirst().map(o->o.tags).orElse(null);
        }
    }
}

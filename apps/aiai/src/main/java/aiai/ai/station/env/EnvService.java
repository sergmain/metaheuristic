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

package aiai.ai.station.env;

import aiai.ai.Consts;
import aiai.ai.Globals;
import aiai.ai.yaml.env.EnvYaml;
import aiai.ai.yaml.env.EnvYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class EnvService {

    private final Globals globals;

    private String env;
    private EnvYaml envYaml;
    private File envYamlFile;

    public EnvService(Globals globals) {
        this.globals = globals;
    }

    @PostConstruct
    public void init() {
        if (!globals.isStationEnabled) {
            return;
        }

        envYamlFile = new File(globals.stationDir, Consts.ENV_YAML_FILE_NAME);
        if (!envYamlFile.exists()) {
            log.warn("#747.01 Station's environment config file doesn't exist: {}", envYamlFile.getPath());
            return;
        }
        try {
            env = FileUtils.readFileToString(envYamlFile, Charsets.UTF_8);
            envYaml = EnvYamlUtils.to(env);
            if (envYaml==null) {
                log.error("#747.07 env.yaml wasn't found or empty. path: {}{}env.yaml", globals.stationDir, File.separatorChar );
                throw new IllegalStateException("Station isn't configured, env.yaml is empty or doesn't exist");
            }
        } catch (IOException e) {
            String es = "#747.11 Error while loading file: " + envYamlFile.getPath();
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
    }

    public String getEnv() {
        synchronized (this) {
            return env;
        }
    }

    private void setEnv(String env) {
        synchronized (this) {
            this.env = env;
        }
    }

    public EnvYaml getEnvYaml() {
        return envYaml;
    }

    public void monitorHotDeployDir() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }

        if (!globals.stationEnvHotDeployDir.exists()) {
            globals.stationEnvHotDeployDir.mkdirs();
        }
        try {
            Files.list(globals.stationEnvHotDeployDir.toPath())
                    .filter( o -> {
                        File f = o.toFile();
                        return f.getName().endsWith(".yaml");
                    })
                    .forEach(dataFile -> {
                        File file = dataFile.toFile();
                        if (file.isFile()) {
                            try (InputStream is = new FileInputStream(file)) {
                                EnvYaml env = EnvYamlUtils.to(is);
                                env.envs.forEach((key, value) -> {
                                    if (envYaml.envs.containsKey(key)) {
                                        log.warn("Environment already has key {}", key);
                                        return;
                                    }
                                    log.info("new env record was added, key: {}, value: {}", key, value);
                                    envYaml.envs.put(key, value);
                                });
                            } catch (Throwable th) {
                                log.error("Can't read file " + file.getAbsolutePath(), th);
                            }
                        }
                        file.delete();
                    });

            String newEnv = EnvYamlUtils.toString(envYaml);
            FileUtils.writeStringToFile(envYamlFile, newEnv, StandardCharsets.UTF_8);
            setEnv(newEnv);
        } catch (Throwable th) {
            log.error("Error", th);
        }
    }

}

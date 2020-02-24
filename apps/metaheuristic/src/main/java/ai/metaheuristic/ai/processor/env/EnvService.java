/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
import ai.metaheuristic.ai.yaml.env.EnvYaml;
import ai.metaheuristic.ai.yaml.env.EnvYamlUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static ai.metaheuristic.ai.Consts.YAML_EXT;
import static ai.metaheuristic.ai.Consts.YML_EXT;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile("processor")
public class EnvService {

    private final Globals globals;

    private String env;
    private EnvYaml envYaml;

    @PostConstruct
    public void init() {
        if (!globals.processorEnabled) {
            return;
        }

        final File envYamlFile = new File(globals.processorDir, Consts.ENV_YAML_FILE_NAME);
        if (!envYamlFile.exists()) {
            if (globals.defaultEnvYamlFile == null) {
                log.warn("#747.020 Processor's env.yaml config file doesn't exist: {}", envYamlFile.getPath());
                throw new IllegalStateException("#747.012 Processor isn't configured, env.yaml is empty or doesn't exist");
            }
            if (!globals.defaultEnvYamlFile.exists()) {
                log.warn("#747.030 Processor's default yaml.yaml file doesn't exist: {}", globals.defaultEnvYamlFile.getAbsolutePath());
                throw new IllegalStateException("#747.014 Processor isn't configured, env.yaml is empty or doesn't exist");
            }
            try {
                FileUtils.copyFile(globals.defaultEnvYamlFile, envYamlFile);
            } catch (IOException e) {
                log.error("#747.035 Error", e);
                throw new IllegalStateException("#747.040 Error while copying "+ globals.defaultEnvYamlFile.getAbsolutePath()+" to " + envYamlFile.getAbsolutePath(), e);
            }
        }

        try {
            env = FileUtils.readFileToString(envYamlFile, Charsets.UTF_8);
        } catch (IOException e) {
            log.error("#747.045 Error", e);
            throw new IllegalStateException("#747.050 Error while reading file: " + envYamlFile.getAbsolutePath(), e);
        }

        envYaml = EnvYamlUtils.to(env);
        if (envYaml==null) {
            log.error("#747.060 env.yaml wasn't found or empty. path: {}{}env.yaml", globals.processorDir, File.separatorChar );
            throw new IllegalStateException("#747.062 Processor isn't configured, env.yaml is empty or doesn't exist");
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
        if (!globals.processorEnabled) {
            return;
        }
        if (!globals.processorEnvHotDeploySupported) {
            return;
        }
        synchronized (this) {
            if (!globals.processorEnvHotDeployDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                globals.processorEnvHotDeployDir.mkdirs();
            }
            try {
                AtomicBoolean changed = new AtomicBoolean(false);
                Files.list(globals.processorEnvHotDeployDir.toPath())
                        .map(Path::toFile)
                        .filter(File::isFile)
                        .filter(f -> {
                            String ext = StrUtils.getExtension(f.getName());
                            if (ext==null) {
                                return false;
                            }
                            return StringUtils.equalsAny(ext.toLowerCase(), YAML_EXT, YML_EXT);
                        })
                        .forEach(file -> {
                            try {
                                if (file.isFile()) {
                                    try (InputStream is = new FileInputStream(file)) {
                                        EnvYaml env = EnvYamlUtils.to(is);
                                        env.envs.forEach((key, value) -> {
                                            if (envYaml.envs.containsKey(key)) {
                                                log.warn("#747.070 Environment already has key {}", key);
                                                return;
                                            }
                                            log.info("new env record was added, key: {}, value: {}", key, value);
                                            envYaml.envs.put(key, value);
                                            changed.set(true);
                                        });
                                    } catch (Throwable th) {
                                        log.error("#747.080 Can't read file " + file.getAbsolutePath(), th);
                                    }
                                }
                            }
                            finally {
                                try {
                                    //noinspection ResultOfMethodCallIgnored
                                    file.delete();
                                } catch (Throwable th) {
                                    log.error("#747.090 Can't delete dir " + file.getPath(), th);
                                }
                            }
                        });

                if (changed.get()) {
                    final String newEnv = EnvYamlUtils.toString(envYaml);
                    final File envYamlFile = new File(globals.processorDir, Consts.ENV_YAML_FILE_NAME);
                    if (envYamlFile.exists()) {
                        log.trace("env.yaml file exists. Make a backup.");
                        File yamlFileBak = new File(globals.processorDir, Consts.ENV_YAML_FILE_NAME + ".bak");
                        //noinspection ResultOfMethodCallIgnored
                        yamlFileBak.delete();
                        if (envYamlFile.exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            envYamlFile.renameTo(yamlFileBak);
                        }
                    }

                    FileUtils.writeStringToFile(envYamlFile, newEnv, StandardCharsets.UTF_8);
                    setEnv(newEnv);
                }
            } catch (Throwable th) {
                log.error("#747.100 Error", th);
            }
        }
    }

}

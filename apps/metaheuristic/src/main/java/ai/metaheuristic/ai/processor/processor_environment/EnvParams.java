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

package ai.metaheuristic.ai.processor.processor_environment;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.core.SystemProcessLauncher;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.env.EnvParamsYaml;
import ai.metaheuristic.commons.yaml.env.EnvParamsYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
public class EnvParams {

    private EnvParamsYaml envYaml;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    public void init(Path processorPath, @Nullable File defaultEnvYamlFile, int taskConsoleOutputMaxLines) {
        writeLock.lock();
        try {
            initInternal(processorPath, defaultEnvYamlFile, taskConsoleOutputMaxLines);
        } finally {
            writeLock.unlock();
        }
    }

    public void initInternal(Path processorPath, @Nullable File defaultEnvYamlFile, int taskConsoleOutputMaxLines) {
        final Path envYamlFile = processorPath.resolve(Consts.ENV_YAML_FILE_NAME);
        if (Files.notExists(envYamlFile)) {
            if (defaultEnvYamlFile==null) {
                log.warn("#747.020 Processor's env.yaml config file doesn't exist: {}", envYamlFile.toAbsolutePath());
                throw new IllegalStateException("#747.012 Processor isn't configured, env.yaml is empty or doesn't exist");
            }
            if (!defaultEnvYamlFile.exists()) {
                log.warn("#747.030 Processor's default yaml.yaml file doesn't exist: {}", defaultEnvYamlFile.getAbsolutePath());
                throw new IllegalStateException("#747.014 Processor isn't configured, env.yaml is empty or doesn't exist");
            }
            try {
                Files.copy(defaultEnvYamlFile.toPath(), envYamlFile);
            } catch (IOException e) {
                log.error("#747.035 Error", e);
                throw new IllegalStateException("#747.040 Error while copying " + defaultEnvYamlFile.getAbsolutePath() +
                                                " to " + envYamlFile.toAbsolutePath(), e);
            }
        }

        String env;
        try {
            env = Files.readString(envYamlFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("#747.045 Error", e);
            throw new IllegalStateException("#747.050 Error while reading file: " + envYamlFile.toAbsolutePath(), e);
        }

        envYaml = EnvParamsYamlUtils.BASE_YAML_UTILS.to(env);
        if (envYaml==null) {
            log.error("#747.060 env.yaml wasn't found or empty. path: {}{}env.yaml", processorPath, File.separatorChar );
            throw new IllegalStateException("#747.062 Processor isn't configured, env.yaml is empty or doesn't exist");
        }
        for (EnvParamsYaml.Env envForVerify : envYaml.envs) {
            if (envForVerify.verify!=null) {
                if (envForVerify.verify.run) {
                    List<String> params = new ArrayList<>();
                    //noinspection ManualArrayToCollectionCopy
                    for (String s : StringUtils.split(envForVerify.exec, ' ')) {
                        //noinspection UseBulkOperation
                        params.add(s);
                    }
                    if (!S.b(envForVerify.verify.params)) {
                        //noinspection ManualArrayToCollectionCopy
                        for (String s : StringUtils.split(envForVerify.verify.params, ' ')) {
                            //noinspection UseBulkOperation
                            params.add(s);
                        }
                    }
                    SystemProcessLauncher.ExecResult result = SystemProcessLauncher.execCmd(params, 10L, taskConsoleOutputMaxLines);
                    if (!result.ok) {
                        System.out.println("!!!");
                        System.out.println("!!!");
                        System.out.println("!!! ======================================================================================================");
                        System.out.println("!!!");
                        System.out.println("!!! Verification of environment was failed. Error while executing " + params);
                        System.out.println("!!! Error: " + result.error);
                        System.out.println("!!!");
                        System.out.println("!!! ======================================================================================================");
                        System.out.println("!!!");
                        System.out.println("!!!");
                        throw new IllegalStateException("Verification of environment was failed, error: " + result.error);
                    }
                }
            }
        }
        verifyCoreCodes(envYaml);
    }

    private static void verifyCoreCodes(EnvParamsYaml envYaml) {
        Set<String> codes = new HashSet<>();
        for (EnvParamsYaml.Core core : envYaml.cores) {
            if (codes.contains(core.code)) {
                throw new IllegalStateException("Core with code "+core.code+" present more than once");
            }
            codes.add(core.code);
        }
    }

    public EnvParamsYaml getEnvParamsYaml() {
        return envYaml;
    }

    @Nullable
    public String getTags(String coreCode) {
        return envYaml.cores.stream().filter(o->o.code.equals(coreCode)).findFirst().map(o->o.tags).orElse(null);
    }
}

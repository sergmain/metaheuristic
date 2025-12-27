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

package ai.metaheuristic.ai.processor.processor_environment;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.commons.system.SystemProcessLauncher;
import ai.metaheuristic.ai.exceptions.TerminateApplicationException;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.env.EnvParamsYaml;
import ai.metaheuristic.commons.yaml.env.EnvParamsYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.io.InputStream;
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

    public void init(Path processorPath,@Nullable EnvYamlProvider envYamlProvider, int taskConsoleOutputMaxLines, boolean verify) {
        writeLock.lock();
        try {
            initInternal(processorPath, envYamlProvider, taskConsoleOutputMaxLines, verify);
        } finally {
            writeLock.unlock();
        }
    }

    private void initInternal(Path processorPath, @Nullable EnvYamlProvider envYamlProvider, int taskConsoleOutputMaxLines, boolean verify) {
        final Path envYamlFile = processorPath.resolve(Consts.ENV_YAML_FILE_NAME);
        if (Files.notExists(envYamlFile)) {
            if (envYamlProvider==null) {
                log.warn("747.020 Processor's env.yaml config file doesn't exist: {}", envYamlFile.toAbsolutePath());
                throw new TerminateApplicationException("747.012 Processor isn't configured, env.yaml is empty or doesn't exist");
            }
            try (InputStream is = envYamlProvider.provide()) {
                Files.copy(is, envYamlFile);
            } catch (IOException e) {
                log.error("747.035 Error", e);
                throw new TerminateApplicationException("747.040 Error while creating env.yaml with " + envYamlProvider.getClass().getName() +
                                                " provider and target path as " + envYamlFile.toAbsolutePath(), e);
            }
        }

        String env;
        try {
            env = Files.readString(envYamlFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("747.045 Error", e);
            throw new TerminateApplicationException("747.050 Error while reading file: " + envYamlFile.toAbsolutePath(), e);
        }

        envYaml = EnvParamsYamlUtils.BASE_YAML_UTILS.to(env);
        if (envYaml==null) {
            log.error("747.060 env.yaml wasn't found or empty. path: {}/env.yaml", processorPath);
            throw new TerminateApplicationException("747.062 Processor isn't configured, env.yaml is empty or doesn't exist");
        }

        verifyProcessCmd(taskConsoleOutputMaxLines, verify);
        verifyCoreCodes(envYaml);
    }

    private void verifyProcessCmd(int taskConsoleOutputMaxLines, boolean verify) {
        if (!verify) {
            return;
        }
        List<List<String>> cmds = new ArrayList<>();
        Set<String> cmdSet = new HashSet<>();
        for (EnvParamsYaml.Env envForVerify : envYaml.envs) {
            if (envForVerify.verify != null) {
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
                    String cmd = String.join(" ", params);
                    if (cmdSet.contains(cmd)) {
                        continue;
                    }
                    cmdSet.add(cmd);
                    cmds.add(params);
                }
            }
        }
        for (List<String> params : cmds) {
            SystemProcessLauncher.ExecResult result = SystemProcessLauncher.execCmd(params, 10L, taskConsoleOutputMaxLines);
            if (!result.ok) {
                log.error("!!!");
                log.error("!!!");
                log.error("!!! ======================================================================================================");
                log.error("!!!");
                log.error("!!! Verification of environment was failed. Error while executing {}", params);
                log.error("!!! Error: {}", result.error);
                log.error("!!!");
                log.error("!!! ======================================================================================================");
                log.error("!!!");
                log.error("!!!");
                throw new TerminateApplicationException("Verification of environment was failed, error: " + result.error);
            }
        }
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

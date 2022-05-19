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
import ai.metaheuristic.ai.core.SystemProcessLauncher;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.FileSystemUtils;
import ai.metaheuristic.commons.yaml.env.EnvParamsYaml;
import ai.metaheuristic.commons.yaml.env.EnvParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
                FileSystemUtils.copyFileWithSync(globals.processor.defaultEnvYamlFile, envYamlFile);
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
                    SystemProcessLauncher.ExecResult result = SystemProcessLauncher.execCmd(params, 10L, globals.processor.taskConsoleOutputMaxLines);
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
        synchronized (this) {
            return envYaml.cores.stream().filter(o->o.code.equals(coreCode)).findFirst().map(o->o.tags).orElse(null);
        }
    }
}

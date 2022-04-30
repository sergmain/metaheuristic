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
package ai.metaheuristic.ai.yaml.processor_status;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BlankYamlParamsException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ProcessorStatusYamlUtilsV3
        extends AbstractParamsYamlUtils<ProcessorStatusYamlV3, ProcessorStatusYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 3;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ProcessorStatusYamlV3.class);
    }

    @NonNull
    @Override
    public ProcessorStatusYaml upgradeTo(@NonNull ProcessorStatusYamlV3 src) {
        src.checkIntegrity();
        ProcessorStatusYaml trg = new ProcessorStatusYaml();
        trg.downloadStatuses = src.downloadStatuses.stream()
                .map( source -> new ProcessorStatusYaml.DownloadStatus(source.functionState,source.functionCode))
                .collect(Collectors.toList());
        if (src.errors!=null) {
            trg.errors = new ArrayList<>(src.errors);
        }
        trg.env = getEnv(src.env);
        if (src.log!=null) {
            trg.log = new ProcessorStatusYaml.Log(src.log.logRequested, src.log.requestedOn, src.log.logReceivedOn);
        }
        BeanUtils.copyProperties(src, trg, "downloadStatuses", "errors", "taskIds");
        trg.checkIntegrity();
        return trg;
    }

    @Nullable
    private static ProcessorStatusYaml.Env getEnv(@Nullable ProcessorStatusYamlV3.EnvV3 envSrc) {
        if (envSrc==null) {
            return null;
        }

        ProcessorStatusYaml.Env env = new ProcessorStatusYaml.Env();

        if (!envSrc.envs.isEmpty()) {
            final Map<String, String> envMap = envSrc.envs.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, HashMap::new));
            env.envs.putAll(envMap);
        }

        if (!envSrc.disk.isEmpty()) {
            env.disk.addAll(envSrc.disk.stream()
                    .map(d -> new ProcessorStatusYaml.DiskStorage(d.code, d.path))
                    .collect(Collectors.toList()));
        }
        if (!envSrc.mirrors.isEmpty()) {
            final Map<String, String> mirrorMap = envSrc.mirrors.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, HashMap::new));
            env.mirrors.putAll(mirrorMap);
        }
        envSrc.quotas.values.stream().map(o->new ProcessorStatusYaml.Quota(o.tag, o.amount, o.disabled)).collect(Collectors.toCollection(()->env.quotas.values));
        env.quotas.limit = envSrc.quotas.limit;
        env.quotas.disabled = envSrc.quotas.disabled;
        env.quotas.defaultValue = envSrc.quotas.defaultValue;
        return env;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull ProcessorStatusYamlV3 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public ProcessorStatusYamlV3 to(@NonNull String s) {
        if (S.b(s)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        final ProcessorStatusYamlV3 p = getYaml().load(s);
        return p;
    }

}

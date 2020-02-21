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
package ai.metaheuristic.ai.yaml.station_status;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.ai.yaml.env.DiskStorage;
import ai.metaheuristic.ai.yaml.env.EnvYaml;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class StationStatusYamlUtilsV1
        extends AbstractParamsYamlUtils<StationStatusYamlV1, StationStatusYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(StationStatusYamlV1.class);
    }

    @Override
    public StationStatusYaml upgradeTo(StationStatusYamlV1 src, Long ... vars) {
        src.checkIntegrity();
        StationStatusYaml trg = new StationStatusYaml();
        trg.downloadStatuses = src.downloadStatuses.stream()
                .map( source -> new StationStatusYaml.DownloadStatus(source.functionState,source.functionCode))
                .collect(Collectors.toList());
        if (src.errors!=null) {
            trg.errors = new ArrayList<>(src.errors);
        }
        if (src.env!=null) {
            trg.env = new EnvYaml();
            if (!src.env.envs.isEmpty()) {
                final Map<String, String> envMap = src.env.envs.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, HashMap::new));
                trg.env.envs.putAll(envMap);
            }

            if (!src.env.disk.isEmpty()) {
                trg.env.disk.addAll(src.env.disk.stream()
                        .map(d -> new DiskStorage(d.code, d.path))
                        .collect(Collectors.toList()));
            }
            if (!src.env.mirrors.isEmpty()) {
                final Map<String, String> mirrorMap = src.env.mirrors.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, HashMap::new));
                trg.env.mirrors.putAll(mirrorMap);
            }
        }
        BeanUtils.copyProperties(src, trg, "downloadStatuses", "errors");
        trg.checkIntegrity();
        return trg;
    }

    @Override
    public Void downgradeTo(Void yaml) {
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
    public String toString(StationStatusYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @Override
    public StationStatusYamlV1 to(String s) {
        if (S.b(s)) {
            return null;
        }
        //noinspection UnnecessaryLocalVariable
        final StationStatusYamlV1 p = getYaml().load(s);
        return p;
    }

}

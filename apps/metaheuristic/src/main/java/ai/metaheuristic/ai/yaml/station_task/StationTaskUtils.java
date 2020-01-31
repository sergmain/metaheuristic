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
package ai.metaheuristic.ai.yaml.station_task;

import ai.metaheuristic.commons.yaml.YamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;

@Slf4j
public class StationTaskUtils {

    private static Yaml getYaml() {
        return YamlUtils.init(StationTask.class);
    }

    public static String toString(StationTask config) {
        return YamlUtils.toString(config, getYaml());
    }

    public static StationTask to(String s) {
        return (StationTask) YamlUtils.to(s, getYaml());
    }

    public static StationTask to(InputStream is) {
        return (StationTask) YamlUtils.to(is, getYaml());
    }

    public static StationTask to(File file) {
        return (StationTask) YamlUtils.to(file, getYaml());
    }
}

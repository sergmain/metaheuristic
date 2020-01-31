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

import ai.metaheuristic.ai.yaml.metadata.SnippetDownloadStatusYaml;
import ai.metaheuristic.ai.yaml.metadata.SnippetDownloadStatusYamlUtilsV1;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.BaseYamlUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.util.Map;

public class StationStatusYamlUtils {

    private static final StationStatusYamlUtilsV1 YAML_UTILS_V_1 = new StationStatusYamlUtilsV1();
    private static final StationStatusYamlUtilsV1 DEFAULT_UTILS = YAML_UTILS_V_1;

    public static final BaseYamlUtils<StationStatusYaml> BASE_YAML_UTILS = new BaseYamlUtils<>(
            Map.of(
                    1, YAML_UTILS_V_1
            ),
            DEFAULT_UTILS
    );


}
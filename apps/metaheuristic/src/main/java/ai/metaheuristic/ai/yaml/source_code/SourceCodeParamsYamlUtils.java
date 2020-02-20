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
package ai.metaheuristic.ai.yaml.source_code;

import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.commons.yaml.versioning.BaseYamlUtils;

import java.util.Map;

public class SourceCodeParamsYamlUtils {

    // Map of minimum required version of TaskParamsYaml related to version of SourceCodeParamsYaml
    // key - version of SourceCodeParamsYaml
    // value - version of TaskParamsYaml
    private static final Map<Integer, Integer> MIN_TASK_PARAMS_YAML_VERSION = Map.of(
            1, 1
    );

    public static int getRequiredVertionOfTaskParamsYaml(int sourceCodeParamsYamlVersion) {
        Integer version = MIN_TASK_PARAMS_YAML_VERSION.get(sourceCodeParamsYamlVersion);
        if (version==null) {
            throw new IllegalStateException("unknown version of SourceCodeParamsYaml, version: " + sourceCodeParamsYamlVersion);
        }
        return version;
    }

    private static final SourceCodeParamsYamlUtilsV1 YAML_UTILS_V_1 = new SourceCodeParamsYamlUtilsV1();
    private static final SourceCodeParamsYamlUtilsV1 DEFAULT_UTILS = YAML_UTILS_V_1;

    public static final BaseYamlUtils<SourceCodeParamsYaml> BASE_YAML_UTILS = new BaseYamlUtils<>(
            Map.of(
                    1, YAML_UTILS_V_1
            ),
            DEFAULT_UTILS
    );


}

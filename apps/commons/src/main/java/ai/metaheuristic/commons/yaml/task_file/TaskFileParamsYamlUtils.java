/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
package ai.metaheuristic.commons.yaml.task_file;

import ai.metaheuristic.commons.yaml.versioning.BaseYamlUtils;

import java.util.Map;

public class TaskFileParamsYamlUtils {

    public static final TaskFileParamsYamlUtilsV1 YAML_UTILS_V_1 = new TaskFileParamsYamlUtilsV1();
    public static final TaskFileParamsYamlUtilsV2 YAML_UTILS_V_2 = new TaskFileParamsYamlUtilsV2();
    public static final TaskFileParamsYamlUtilsV2 DEFAULT_UTILS = YAML_UTILS_V_2;

    public static final BaseYamlUtils<TaskFileParamsYaml> BASE_YAML_UTILS = new BaseYamlUtils<>(
            Map.of(
                    1, YAML_UTILS_V_1,
                    2, YAML_UTILS_V_2
            ),
            DEFAULT_UTILS
    );

}

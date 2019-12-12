/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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
package ai.metaheuristic.commons.yaml.task;

import ai.metaheuristic.api.data.task.TaskParamsYamlV4;
import ai.metaheuristic.commons.yaml.snippet.SnippetConfigYaml;
import ai.metaheuristic.commons.yaml.versioning.BaseYamlUtils;
import ai.metaheuristic.api.data.task.TaskParamsYaml;

import java.util.Map;

public class TaskParamsYamlUtils {

    private static final TaskParamsYamlUtilsV1 YAML_UTILS_V_1 = new TaskParamsYamlUtilsV1();
    private static final TaskParamsYamlUtilsV2 YAML_UTILS_V_2 = new TaskParamsYamlUtilsV2();
    private static final TaskParamsYamlUtilsV3 YAML_UTILS_V_3 = new TaskParamsYamlUtilsV3();
    private static final TaskParamsYamlUtilsV4 YAML_UTILS_V_4 = new TaskParamsYamlUtilsV4();
    private static final TaskParamsYamlUtilsV4 DEFAULT_UTILS = YAML_UTILS_V_4;

    public static final BaseYamlUtils<TaskParamsYaml> BASE_YAML_UTILS = new BaseYamlUtils<>(
            Map.of(
                    1, YAML_UTILS_V_1,
                    2, YAML_UTILS_V_2,
                    3, YAML_UTILS_V_3,
                    4, YAML_UTILS_V_4
            ),
            DEFAULT_UTILS
    );

}

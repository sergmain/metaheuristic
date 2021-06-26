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
package ai.metaheuristic.ai.yaml.exec_context;

import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.yaml.versioning.BaseYamlUtils;

import java.util.Map;

public class ExecContextParamsYamlUtils {

    private static final ExecContextParamsYamlUtilsV1 YAML_UTILS_V_1 = new ExecContextParamsYamlUtilsV1();
    private static final ExecContextParamsYamlUtilsV2 YAML_UTILS_V_2 = new ExecContextParamsYamlUtilsV2();
    private static final ExecContextParamsYamlUtilsV3 YAML_UTILS_V_3 = new ExecContextParamsYamlUtilsV3();
    private static final ExecContextParamsYamlUtilsV4 YAML_UTILS_V_4 = new ExecContextParamsYamlUtilsV4();
    private static final ExecContextParamsYamlUtilsV4 DEFAULT_UTILS = YAML_UTILS_V_4;

    public static final BaseYamlUtils<ExecContextParamsYaml> BASE_YAML_UTILS = new BaseYamlUtils<>(
            Map.of(
                    1, YAML_UTILS_V_1,
                    2, YAML_UTILS_V_2,
                    3, YAML_UTILS_V_3,
                    4, YAML_UTILS_V_4
            ),
            DEFAULT_UTILS
    );
}

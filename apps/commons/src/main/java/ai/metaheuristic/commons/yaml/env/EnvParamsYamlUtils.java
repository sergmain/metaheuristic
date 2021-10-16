/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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
package ai.metaheuristic.commons.yaml.env;

import ai.metaheuristic.commons.yaml.versioning.BaseYamlUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class EnvParamsYamlUtils {
    private static final EnvParamsYamlUtilsV1 YAML_UTILS_V_1 = new EnvParamsYamlUtilsV1();
    private static final EnvParamsYamlUtilsV2 YAML_UTILS_V_2 = new EnvParamsYamlUtilsV2();
    private static final EnvParamsYamlUtilsV3 YAML_UTILS_V_3 = new EnvParamsYamlUtilsV3();
    private static final EnvParamsYamlUtilsV3 DEFAULT_UTILS = YAML_UTILS_V_3;

    public static final BaseYamlUtils<EnvParamsYaml> BASE_YAML_UTILS = new BaseYamlUtils<>(
            Map.of(
                    1, YAML_UTILS_V_1,
                    2, YAML_UTILS_V_2,
                    3, YAML_UTILS_V_3
            ),
            DEFAULT_UTILS
    );
}

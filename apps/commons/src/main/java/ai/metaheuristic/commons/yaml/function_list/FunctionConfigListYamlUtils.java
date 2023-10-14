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
package ai.metaheuristic.commons.yaml.function_list;

import ai.metaheuristic.commons.yaml.versioning.BaseYamlUtils;

import java.util.Map;

public class FunctionConfigListYamlUtils {

    private static final FunctionConfigListYamlUtilsV1 UTILS_V_1 = new FunctionConfigListYamlUtilsV1();
    private static final FunctionConfigListYamlUtilsV2 UTILS_V_2 = new FunctionConfigListYamlUtilsV2();
    private static final FunctionConfigListYamlUtilsV2 DEFAULT_UTILS = UTILS_V_2;

    public static final BaseYamlUtils<FunctionConfigListYaml> UTILS = new BaseYamlUtils<>(
            Map.of(
                    1, UTILS_V_1,
                    2, UTILS_V_2
            ),
            DEFAULT_UTILS
    );
}

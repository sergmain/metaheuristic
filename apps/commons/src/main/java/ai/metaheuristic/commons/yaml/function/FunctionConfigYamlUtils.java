/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
package ai.metaheuristic.commons.yaml.function;

import ai.metaheuristic.commons.yaml.versioning.BaseYamlUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class FunctionConfigYamlUtils {

    private static final FunctionConfigYamlUtilsV1 UTILS_V_1 = new FunctionConfigYamlUtilsV1();
    private static final FunctionConfigYamlUtilsV2 UTILS_V_2 = new FunctionConfigYamlUtilsV2();
    private static final FunctionConfigYamlUtilsV2 DEFAULT_UTILS = UTILS_V_2;

    public static final BaseYamlUtils<FunctionConfigYaml> UTILS = new BaseYamlUtils<>(
            Map.of(
                    1, UTILS_V_1,
                    2, UTILS_V_2
            ),
            DEFAULT_UTILS
    );
}

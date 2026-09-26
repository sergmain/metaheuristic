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

package ai.metaheuristic.ai.yaml.execution_gate;

import ai.metaheuristic.commons.json.versioning_json.BaseJsonUtils;

import java.util.Map;

/**
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
public class ExecutionGateParamsUtils {

    private static final ExecutionGateParamsUtilsV1 UTILS_V_1 = new ExecutionGateParamsUtilsV1();
    private static final ExecutionGateParamsUtilsV1 DEFAULT_UTILS = UTILS_V_1;

    public static final BaseJsonUtils<ExecutionGateParams> BASE_UTILS = new BaseJsonUtils<>(
            Map.of(
                    1, UTILS_V_1
            ),
            DEFAULT_UTILS
    );

}

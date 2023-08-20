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

package ai.metaheuristic.ai.yaml.experiment_result;

import ai.metaheuristic.api.data.experiment_result.ExperimentResultParams;
import ai.metaheuristic.commons.json.versioning_json.BaseJsonUtils;

import java.util.Map;

/**
 * @author Serge
 * Date: 4/16/2021
 * Time: 6:14 PM
 */
public class ExperimentResultParamsJsonUtils {

    private static final ExperimentResultParamsJsonUtilsV1 UTILS_V_1 = new ExperimentResultParamsJsonUtilsV1();
    private static final ExperimentResultParamsJsonUtilsV2 UTILS_V_2 = new ExperimentResultParamsJsonUtilsV2();
    private static final ExperimentResultParamsJsonUtilsV2 DEFAULT_UTILS = UTILS_V_2;

    public static final BaseJsonUtils<ExperimentResultParams> BASE_UTILS = new BaseJsonUtils<>(
            Map.of(
                    1, UTILS_V_1,
                    2, UTILS_V_2
            ),
            DEFAULT_UTILS
    );
}

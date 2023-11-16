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

package ai.metaheuristic.ai.functions.communication;

import ai.metaheuristic.commons.yaml.versioning.BaseYamlUtils;

import java.util.Map;

/**
 * @author Sergio Lissner
 * Date: 11/15/2023
 * Time: 7:09 PM
 */
public class FunctionRepositoryRequestParamsUtils {
    private static final FunctionRepositoryRequestParamsUtilsV1 UTILS_V_1 = new FunctionRepositoryRequestParamsUtilsV1();
    private static final FunctionRepositoryRequestParamsUtilsV1 DEFAULT_UTILS = UTILS_V_1;

    public static final BaseYamlUtils<FunctionRepositoryRequestParams> UTILS = new BaseYamlUtils<>(
        Map.of(
            1, UTILS_V_1
        ),
        DEFAULT_UTILS
    );
}

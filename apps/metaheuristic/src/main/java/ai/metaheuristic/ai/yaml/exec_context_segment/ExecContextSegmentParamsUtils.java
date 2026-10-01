/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.yaml.exec_context_segment;

import ai.metaheuristic.commons.json.versioning_json.BaseJsonUtils;

import java.util.Map;

/**
 * The JSON utils of {@link ExecContextSegmentParams}.
 */
public class ExecContextSegmentParamsUtils {

    private static final ExecContextSegmentParamsUtilsV1 UTILS_V_1 = new ExecContextSegmentParamsUtilsV1();
    private static final ExecContextSegmentParamsUtilsV1 DEFAULT_UTILS = UTILS_V_1;

    public static final BaseJsonUtils<ExecContextSegmentParams> BASE_UTILS = new BaseJsonUtils<>(
            Map.of(
                    1, UTILS_V_1
            ),
            DEFAULT_UTILS
    );

}

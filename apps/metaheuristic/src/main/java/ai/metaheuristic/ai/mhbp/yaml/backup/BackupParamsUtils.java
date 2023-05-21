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

package ai.metaheuristic.ai.mhbp.yaml.backup;

import ai.metaheuristic.commons.yaml.versioning.BaseYamlUtils;

import java.util.Map;

public class BackupParamsUtils {

    private static final BackupParamsUtilsV1 UTILS_V_1 = new BackupParamsUtilsV1();
    private static final BackupParamsUtilsV1 DEFAULT_UTILS = UTILS_V_1;

    public static final BaseYamlUtils<BackupParams> UTILS = new BaseYamlUtils<>(
            Map.of(
                    1, UTILS_V_1
            ),
            DEFAULT_UTILS
    );
}

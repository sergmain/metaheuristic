/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.ai.yaml.series;

import ai.metaheuristic.commons.yaml.versioning.BaseYamlUtils;

import java.util.Map;

/**
 * @author Serge
 * Date: 3/30/2021
 * Time: 1:47 PM
 */
public class SeriesParamsYamlUtils {

    private static final SeriesParamsYamlUtilsV1 YAML_UTILS_V_1 = new SeriesParamsYamlUtilsV1();
    private static final SeriesParamsYamlUtilsV1 DEFAULT_UTILS = YAML_UTILS_V_1;

    public static final BaseYamlUtils<SeriesParamsYaml> BASE_YAML_UTILS = new BaseYamlUtils<>(
            Map.of(
                    1, YAML_UTILS_V_1
            ),
            DEFAULT_UTILS
    );


}

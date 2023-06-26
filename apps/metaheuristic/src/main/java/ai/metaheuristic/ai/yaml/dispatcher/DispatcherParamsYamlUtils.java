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

package ai.metaheuristic.ai.yaml.dispatcher;

import ai.metaheuristic.commons.yaml.versioning.BaseYamlUtils;

import java.util.Map;

/**
 * @author Serge
 * Date: 4/19/2020
 * Time: 4:33 PM
 */
public class DispatcherParamsYamlUtils {

    private static final DispatcherParamsYamlUtilsV1 YAML_UTILS_V_1 = new DispatcherParamsYamlUtilsV1();
    private static final DispatcherParamsYamlUtilsV2 YAML_UTILS_V_2 = new DispatcherParamsYamlUtilsV2();
    private static final DispatcherParamsYamlUtilsV2 DEFAULT_UTILS = YAML_UTILS_V_2;

    public static final BaseYamlUtils<DispatcherParamsYaml> BASE_YAML_UTILS = new BaseYamlUtils<>(
            Map.of(
                    1, YAML_UTILS_V_1,
                    2, YAML_UTILS_V_2
            ),
            DEFAULT_UTILS
    );
}

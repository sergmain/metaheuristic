/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.yaml.communication.processor;

import ai.metaheuristic.commons.yaml.versioning.BaseYamlUtils;

import java.util.Map;

/**
 * @author Serge
 * Date: 8/29/2019
 * Time: 6:00 PM
 */
public class ProcessorCommParamsYamlUtils {

    private static final ProcessorCommParamsYamlUtilsV1 YAML_UTILS_V_1 = new ProcessorCommParamsYamlUtilsV1();
    private static final ProcessorCommParamsYamlUtilsV2 YAML_UTILS_V_2 = new ProcessorCommParamsYamlUtilsV2();
    private static final ProcessorCommParamsYamlUtilsV3 YAML_UTILS_V_3 = new ProcessorCommParamsYamlUtilsV3();
    private static final ProcessorCommParamsYamlUtilsV3 DEFAULT_UTILS = YAML_UTILS_V_3;

    public static final BaseYamlUtils<ProcessorCommParamsYaml> BASE_YAML_UTILS = new BaseYamlUtils<>(
            Map.of(
                    1, YAML_UTILS_V_1,
                    2, YAML_UTILS_V_2,
                    3, YAML_UTILS_V_3
            ),
            DEFAULT_UTILS
    );
}

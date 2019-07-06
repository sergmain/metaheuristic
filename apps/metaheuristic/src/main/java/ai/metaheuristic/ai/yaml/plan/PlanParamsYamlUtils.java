/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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
package ai.metaheuristic.ai.yaml.plan;

import ai.metaheuristic.commons.yaml.versioning.BaseYamlUtils;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;

import java.util.Map;

public class PlanParamsYamlUtils {

    // Map of minimum required version of TaskParamsYaml related to version of PlanParamsYamlV5
    // key - version of PlanParamsYamlV5
    // value - version of TaskParamsYaml
    private static final Map<Integer, Integer> MIN_TASK_PARAMS_YAML_VERSION = Map.of(
            1, 1,
            2, 1,
            3, 2,
            4, 2,
            5, 2
    );

    public static int getRequiredVertionOfTaskParamsYaml(int planParamsYamlVersion) {
        Integer version = MIN_TASK_PARAMS_YAML_VERSION.get(planParamsYamlVersion);
        if (version==null) {
            throw new IllegalStateException("unknown version of PlanParamsYaml, version: " + planParamsYamlVersion);
        }
        return version;
    }

    private static final PlanParamsYamlUtilsV1 YAML_UTILS_V_1 = new PlanParamsYamlUtilsV1();
    private static final PlanParamsYamlUtilsV2 YAML_UTILS_V_2 = new PlanParamsYamlUtilsV2();
    private static final PlanParamsYamlUtilsV3 YAML_UTILS_V_3 = new PlanParamsYamlUtilsV3();
    private static final PlanParamsYamlUtilsV4 YAML_UTILS_V_4 = new PlanParamsYamlUtilsV4();
    private static final PlanParamsYamlUtilsV5 YAML_UTILS_V_5 = new PlanParamsYamlUtilsV5();
    private static final PlanParamsYamlUtilsV5 DEFAULT_UTILS = YAML_UTILS_V_5;

    public static final BaseYamlUtils<PlanParamsYaml> BASE_YAML_UTILS = new BaseYamlUtils<>(
            Map.of(
                    1, YAML_UTILS_V_1,
                    2, YAML_UTILS_V_2,
                    3, YAML_UTILS_V_3,
                    4, YAML_UTILS_V_4,
                    5, YAML_UTILS_V_5
            ),
            DEFAULT_UTILS
    );


}

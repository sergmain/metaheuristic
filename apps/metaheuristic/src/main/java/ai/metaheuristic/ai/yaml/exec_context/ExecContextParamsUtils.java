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
package ai.metaheuristic.ai.yaml.exec_context;

import ai.metaheuristic.api.data.exec_context.ExecContextParams;
import ai.metaheuristic.commons.yaml.versioning.BaseYamlUtils;

import java.util.Map;

/**
 * <b>!!! BEFORE MAKING ANY EDITION IN THIS CLASS, READ <a href="https://github.com/sergmain/metaheuristic/wiki/multi-versioning-mechanic">...</a></b>
 * <br/>
 *
 */
public class ExecContextParamsUtils {

    private static final ExecContextParamsUtilsV1 YAML_UTILS_V_1 = new ExecContextParamsUtilsV1();
    private static final ExecContextParamsUtilsV2 YAML_UTILS_V_2 = new ExecContextParamsUtilsV2();
    private static final ExecContextParamsUtilsV3 YAML_UTILS_V_3 = new ExecContextParamsUtilsV3();
    private static final ExecContextParamsUtilsV4 YAML_UTILS_V_4 = new ExecContextParamsUtilsV4();
    private static final ExecContextParamsUtilsV5 YAML_UTILS_V_5 = new ExecContextParamsUtilsV5();
    private static final ExecContextParamsUtilsV6 YAML_UTILS_V_6 = new ExecContextParamsUtilsV6();
    private static final ExecContextParamsUtilsV6 DEFAULT_UTILS = YAML_UTILS_V_6;

    public static final BaseYamlUtils<ExecContextParams> BASE_UTILS = new BaseYamlUtils<>(
            Map.of(
                    1, YAML_UTILS_V_1,
                    2, YAML_UTILS_V_2,
                    3, YAML_UTILS_V_3,
                    4, YAML_UTILS_V_4,
                    5, YAML_UTILS_V_5,
                    6, YAML_UTILS_V_6
            ),
            DEFAULT_UTILS
    );
}

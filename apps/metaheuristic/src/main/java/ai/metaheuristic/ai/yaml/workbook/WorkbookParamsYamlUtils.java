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
package ai.metaheuristic.ai.yaml.workbook;

import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.commons.yaml.versioning.BaseYamlUtils;

import java.util.Map;

public class WorkbookParamsYamlUtils {


    private static final WorkbookParamsYamlUtilsV1 YAML_UTILS_V_1 = new WorkbookParamsYamlUtilsV1();
    private static final WorkbookParamsYamlUtilsV2 YAML_UTILS_V_2 = new WorkbookParamsYamlUtilsV2();
    private static final WorkbookParamsYamlUtilsV2 DEFAULT_UTILS = YAML_UTILS_V_2;

    public static final BaseYamlUtils<WorkbookParamsYaml> BASE_YAML_UTILS = new BaseYamlUtils<>(
            Map.of(1, YAML_UTILS_V_1, 2, YAML_UTILS_V_2),
            DEFAULT_UTILS
    );

}

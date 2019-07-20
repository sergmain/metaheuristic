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

package ai.metaheuristic.ai.launchpad.plan;

import ai.metaheuristic.ai.yaml.workbook.WorkbookParamsYamlUtils;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYamlV1;

public class PlanUtils {

    public static String getResourceCode(Long workbookId, String processCode, String snippetName, int processOrder, int snippetIdx) {
        return String.format("%d-%d-%s-%s-%d", workbookId, processOrder, snippetName, processCode, snippetIdx);
    }

    public static WorkbookParamsYaml parseToWorkbookParamsYaml(String inputResourceParams) {
        WorkbookParamsYamlV1 v1 = (WorkbookParamsYamlV1) WorkbookParamsYamlUtils.BASE_YAML_UTILS.getForVersion(1).to(inputResourceParams);
        WorkbookParamsYaml wpy = new WorkbookParamsYaml();
        wpy.workbookYaml.poolCodes.putAll(v1.poolCodes);
        return wpy;
    }
}

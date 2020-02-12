/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.source_code;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.yaml.workbook.WorkbookParamsYamlUtils;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYamlV1;

import java.util.ArrayList;

public class SourceCodeUtils {

    public static String getResourceCode(Long workbookId, String processCode, String snippetName, int processOrder, int snippetIdx) {
        return String.format("%d-%d-%s-%s-%d", workbookId, processOrder, snippetName, processCode, snippetIdx);
    }

    public static WorkbookParamsYaml.WorkbookYaml parseToWorkbookParamsYaml(String inputResourceParams) {
        // we're using V1 because inputResourceParams has a user-generated value in format of V1
        WorkbookParamsYamlV1 v1 = (WorkbookParamsYamlV1) WorkbookParamsYamlUtils.BASE_YAML_UTILS.getForVersion(1).to(inputResourceParams);
        WorkbookParamsYaml.WorkbookYaml wrc = new WorkbookParamsYaml.WorkbookYaml();
        wrc.poolCodes.putAll(v1.poolCodes);
        return wrc;
    }

    public static WorkbookParamsYaml.WorkbookYaml asWorkbookParamsYaml(String variable) {
        WorkbookParamsYaml.WorkbookYaml wrc = new WorkbookParamsYaml.WorkbookYaml();
        wrc.poolCodes.computeIfAbsent(Consts.MH_WORKBOOK_INPUT_VARIABLE, o->new ArrayList<>()).add(variable);
        return wrc;
    }

}

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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.launchpad.batch.BatchService;
import ai.metaheuristic.ai.yaml.workbook.WorkbookParamsYamlUtils;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYamlV1;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class PlanUtils {

    public static String getResourceCode(Long workbookId, String processCode, String snippetName, int processOrder, int snippetIdx) {
        return String.format("%d-%d-%s-%s-%d", workbookId, processOrder, snippetName, processCode, snippetIdx);
    }

    public static WorkbookParamsYaml.WorkbookYaml prepareResourceCodes(String poolCode, String inputResourceParams) {
        //noinspection UnnecessaryLocalVariable
        WorkbookParamsYaml.WorkbookYaml resourceCodes = StringUtils.isNotBlank(inputResourceParams)
                ? parseToWorkbookParamsYaml(inputResourceParams)
                : asWorkbookParamsYaml(poolCode);
        return resourceCodes;
    }

    public static WorkbookParamsYaml.WorkbookYaml parseToWorkbookParamsYaml(String inputResourceParams) {
        // we're using V1 because inputResourceParams has a user-generated value in format of V1
        WorkbookParamsYamlV1 v1 = (WorkbookParamsYamlV1) WorkbookParamsYamlUtils.BASE_YAML_UTILS.getForVersion(1).to(inputResourceParams);
        WorkbookParamsYaml.WorkbookYaml wrc = new WorkbookParamsYaml.WorkbookYaml();
        wrc.poolCodes.putAll(v1.poolCodes);
        return wrc;
    }

    private static WorkbookParamsYaml.WorkbookYaml asWorkbookParamsYaml(String poolCode) {
        WorkbookParamsYaml.WorkbookYaml wrc = new WorkbookParamsYaml.WorkbookYaml();
        wrc.poolCodes.computeIfAbsent(Consts.WORKBOOK_INPUT_TYPE, o->new ArrayList<>()).add(poolCode);
        return wrc;
    }

    public static WorkbookParamsYaml.WorkbookYaml initWorkbookParamsYaml(String mainPoolCode, String attachPoolCode, List<String> attachmentCodes) {
        WorkbookParamsYaml.WorkbookYaml wy = new WorkbookParamsYaml.WorkbookYaml();
        wy.preservePoolNames = true;
        wy.poolCodes.computeIfAbsent(Consts.MAIN_DOCUMENT_POOL_CODE_FOR_BATCH, o-> new ArrayList<>()).add(mainPoolCode);
        if (attachmentCodes.isEmpty()) {
            return wy;
        }
        wy.poolCodes.computeIfAbsent(BatchService.ATTACHMENTS_POOL_CODE, o-> new ArrayList<>()).add(attachPoolCode);
        return wy;
    }
}

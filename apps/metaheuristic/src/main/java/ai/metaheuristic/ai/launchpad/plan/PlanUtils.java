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

    public static WorkbookParamsYaml.WorkbookResourceCodes prepareResourceCodes(String poolCode, String inputResourceParams) {
        //noinspection UnnecessaryLocalVariable
        WorkbookParamsYaml.WorkbookResourceCodes resourceCodes = StringUtils.isNotBlank(inputResourceParams)
                ? parseToWorkbookParamsYaml(inputResourceParams)
                : asWorkbookParamsYaml(poolCode);
        return resourceCodes;
    }

    public static WorkbookParamsYaml.WorkbookResourceCodes parseToWorkbookParamsYaml(String inputResourceParams) {
        WorkbookParamsYamlV1 v1 = (WorkbookParamsYamlV1) WorkbookParamsYamlUtils.BASE_YAML_UTILS.getForVersion(1).to(inputResourceParams);
        WorkbookParamsYaml.WorkbookResourceCodes wrc = new WorkbookParamsYaml.WorkbookResourceCodes();
        wrc.poolCodes.putAll(v1.poolCodes);
        return wrc;
    }

    private static WorkbookParamsYaml.WorkbookResourceCodes asWorkbookParamsYaml(String poolCode) {
        WorkbookParamsYaml.WorkbookResourceCodes wrc = new WorkbookParamsYaml.WorkbookResourceCodes();
        wrc.poolCodes.computeIfAbsent(Consts.WORKBOOK_INPUT_TYPE, o->new ArrayList<>()).add(poolCode);
        return wrc;
    }

    public static WorkbookParamsYaml.WorkbookResourceCodes initWorkbookParamsYaml(String mainPoolCode, String attachPoolCode, List<String> attachmentCodes) {
        WorkbookParamsYaml.WorkbookResourceCodes wy = new WorkbookParamsYaml.WorkbookResourceCodes();
        wy.preservePoolNames = true;
        wy.poolCodes.computeIfAbsent(Consts.MAIN_DOCUMENT_POOL_CODE_FOR_BATCH, o-> new ArrayList<>()).add(mainPoolCode);
        if (attachmentCodes.isEmpty()) {
            return wy;
        }
        wy.poolCodes.computeIfAbsent(BatchService.ATTACHMENTS_POOL_CODE, o-> new ArrayList<>()).add(attachPoolCode);
        return wy;
    }
}

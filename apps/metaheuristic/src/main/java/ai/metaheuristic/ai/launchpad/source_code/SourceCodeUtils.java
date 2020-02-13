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
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYamlV1;

import java.util.ArrayList;

public class SourceCodeUtils {

    public static String getResourceCode(Long workbookId, String processCode, String snippetName, int processOrder, int snippetIdx) {
        return String.format("%d-%d-%s-%s-%d", workbookId, processOrder, snippetName, processCode, snippetIdx);
    }

    public static ExecContextParamsYaml.ExecContextYaml parseToExecContextParamsYaml(String inputResourceParams) {
        // we're using V1 because inputResourceParams has a user-generated value in format of V1
        ExecContextParamsYamlV1 v1 = (ExecContextParamsYamlV1) ExecContextParamsYamlUtils.BASE_YAML_UTILS.getForVersion(1).to(inputResourceParams);
        ExecContextParamsYaml.ExecContextYaml wrc = new ExecContextParamsYaml.ExecContextYaml();
        // ???
        wrc.variables.putAll(v1.poolCodes);
        return wrc;
    }

    public static ExecContextParamsYaml.ExecContextYaml asExecContextParamsYaml(String variable) {
        ExecContextParamsYaml.ExecContextYaml wrc = new ExecContextParamsYaml.ExecContextYaml();
        wrc.variables.computeIfAbsent(Consts.MH_WORKBOOK_INPUT_VARIABLE, o->new ArrayList<>()).add(variable);
        return wrc;
    }

}

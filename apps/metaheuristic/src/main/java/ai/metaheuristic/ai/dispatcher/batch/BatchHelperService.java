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

package ai.metaheuristic.ai.dispatcher.batch;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Serge
 * Date: 9/29/2020
 * Time: 4:57 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class BatchHelperService {

    private final VariableService variableService;

    public String findUploadedFilenameForBatchId(Long execContextId, ExecContextParamsYaml ecpy, @Nullable String defaultName) {
        String defName = S.b(defaultName) ? Consts.RESULT_ZIP : defaultName;
        String startInputVariableName = ecpy.variables.startInputAs;
        if (S.b(startInputVariableName)) {
            return defName;
        }
        List<String> filenames = variableService.getFilenameByVariableAndExecContextId(execContextId, startInputVariableName);
        if (filenames.isEmpty()) {
            return defName;
        }
        if (filenames.size()>1) {
            log.warn("something wrong, too many startInputAs variables: " + filenames);
        }
        return filenames.get(0);
    }


}

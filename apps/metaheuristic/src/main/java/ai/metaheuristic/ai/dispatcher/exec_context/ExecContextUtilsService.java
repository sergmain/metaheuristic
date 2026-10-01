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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * this is service which contains only methods with access to low-level serices, i.e. xxxCache, xxxRepository
 *
 * @author Serge
 * Date: 8/19/2021
 * Time: 10:12 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecContextUtilsService {

    private final ai.metaheuristic.ai.dispatcher.exec_context_segment.ExecContextSegmentReadService segmentReadService;
    private final VariableTxService variableTxService;

    @SuppressWarnings("DataFlowIssue")
    public String getExtensionForVariable(Long execContextId, Long variableId, String defaultExt) {
        Variable variable = variableTxService.getVariable(variableId);
        if (variable==null) {
            return defaultExt;
        }
        final EnumsApi.VariableType variableType = variable.getDataStorageParams().type;
        if (variableType!=null && variableType!=EnumsApi.VariableType.unknown) {
            return variableType.ext;
        }
        // 041 Phase 12: the producer's output entry, from the segment owning the variable's ctx
        String ext = segmentReadService.outputExt(execContextId, variable.taskContextId, variableId);
        return ext!=null ? ext : defaultExt;
    }

    /** 041 Phase 12: every variable-state entry from the segments, input flags derived at read time. */
    public List<ExecContextApiData.VariableState> getExecContextVariableStates(Long execContextId) {
        return segmentReadService.variableStates(execContextId);
    }
}

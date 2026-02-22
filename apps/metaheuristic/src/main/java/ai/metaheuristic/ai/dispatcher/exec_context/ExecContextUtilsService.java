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

import ai.metaheuristic.ai.dispatcher.beans.ExecContextVariableState;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateTxService;
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

    private final ExecContextVariableStateTxService execContextVariableStateCache;
    private final VariableTxService variableTxService;

    @SuppressWarnings("DataFlowIssue")
    public String getExtensionForVariable(Long execContextVariableStateId, Long variableId, String defaultExt) {
        Variable variable = variableTxService.getVariable(variableId);
        if (variable==null) {
            return defaultExt;
        }
        final EnumsApi.VariableType variableType = variable.getDataStorageParams().type;
        if (variableType!=null && variableType!=EnumsApi.VariableType.unknown) {
            return variableType.ext;
        }
        List<ExecContextApiData.VariableState> variableStates = getExecContextVariableStates(execContextVariableStateId);
        String ext = variableStates.stream()
                .filter(o->o.outputs!=null)
                .flatMap(o->o.outputs.stream())
                .filter(o->o.id.equals(variableId) && !S.b(o.ext))
                .findFirst().map(o->o.ext)
                .orElse(defaultExt) ;
        return ext;
    }

    public List<ExecContextApiData.VariableState> getExecContextVariableStates(@Nullable Long execContextVariableStateId) {
        if (execContextVariableStateId==null) {
            return List.of();
        }
        ExecContextVariableState ecvs = execContextVariableStateCache.findById(execContextVariableStateId);
        return ecvs!=null ? ecvs.getExecContextVariableStateInfo().states : List.of();
    }
}

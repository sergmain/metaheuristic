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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextVariableState;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateCache;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

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
@RequiredArgsConstructor
public class ExecContextUtilsService {

    private final ExecContextVariableStateCache execContextVariableStateCache;

    public String getExtensionForVariable(Long execContextVariableStateId, Long variableId, String defaultExt) {
        ExecContextApiData.ExecContextVariableStates info = getExecContextVariableStates(execContextVariableStateId);
        String ext = info.tasks.stream()
                .filter(o->o.outputs!=null)
                .flatMap(o->o.outputs.stream())
                .filter(o->o.id.equals(variableId) && !S.b(o.ext))
                .findFirst().map(o->o.ext)
                .orElse(defaultExt) ;
        return ext;
    }

    public ExecContextApiData.ExecContextVariableStates getExecContextVariableStates(@Nullable Long execContextVariableStateId) {
        ExecContextApiData.ExecContextVariableStates info;
        if (execContextVariableStateId==null) {
            info = new ExecContextApiData.ExecContextVariableStates();
        }
        else {
            ExecContextVariableState ecvs = execContextVariableStateCache.findById(execContextVariableStateId);
            info = ecvs!=null ? ecvs.getExecContextVariableStateInfo() : new ExecContextApiData.ExecContextVariableStates();
        }
        return info;
    }
}

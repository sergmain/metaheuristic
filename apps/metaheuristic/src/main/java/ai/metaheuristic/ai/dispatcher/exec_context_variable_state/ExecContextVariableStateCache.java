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

package ai.metaheuristic.ai.dispatcher.exec_context_variable_state;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextVariableState;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextVariableStateRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 3/20/2021
 * Time: 2:41 AM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextVariableStateCache {

    private final ExecContextVariableStateRepository execContextVariableStateRepository;
    private final ExecContextSyncService execContextSyncService;

    public ExecContextVariableState save(ExecContextVariableState execContextVariableState) {
        TxUtils.checkTxExists();

        if (execContextVariableState.execContextId==null) {
            throw new IllegalStateException(" (execContextVariableState.execContextId==null)");
        }
        execContextSyncService.checkWriteLockPresent(execContextVariableState.execContextId);
        return execContextVariableStateRepository.save(execContextVariableState);
    }

    @Nullable
    public ExecContextVariableState findById(Long id) {
        return execContextVariableStateRepository.findById(id).orElse(null);
    }
}

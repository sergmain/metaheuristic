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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.dispatcher.ExecContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 1/18/2020
 * Time: 3:34 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextFSM {

    private final ExecContextSyncService execContextSyncService;
    private final ExecContextCache execContextCache;

    public void toState(Long execContextId, EnumsApi.ExecContextState state) {
        execContextSyncService.getWithSyncNullable(execContextId, execContext -> {
            if (execContext.state !=state.code) {
                execContext.setState(state.code);
                execContextCache.save(execContext);
            }
            return null;
        });
    }

    private void toStateWithCompletion(Long execContextId, EnumsApi.ExecContextState state) {
        execContextSyncService.getWithSyncNullable(execContextId, execContext -> {
            if (execContext.state !=state.code || execContext.completedOn==null) {
                execContext.setCompletedOn(System.currentTimeMillis());
                execContext.setState(state.code);
                execContextCache.save(execContext);
            }
            return null;
        });
    }

    public void toStarted(ExecContext execContext) {
        toStarted(execContext.getId());
    }

    private void toStarted(Long execContextId) {
        toState(execContextId, EnumsApi.ExecContextState.STARTED);
    }

    public void toStopped(Long execContextId) {
        toState(execContextId, EnumsApi.ExecContextState.STOPPED);
    }

    public void toProduced(Long execContextId) {
        toState(execContextId, EnumsApi.ExecContextState.PRODUCED);
    }

    public void toFinished(Long execContextId) {
        toStateWithCompletion(execContextId, EnumsApi.ExecContextState.FINISHED);
    }

    public void toError(Long execContextId) {
        toStateWithCompletion(execContextId, EnumsApi.ExecContextState.ERROR);
    }


}

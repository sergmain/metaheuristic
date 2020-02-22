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

import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.launchpad.ExecContext;
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
    private final SourceCodeCache sourceCodeCache;

    public void toState(Long execContextId, EnumsApi.ExecContextState state) {
        execContextSyncService.getWithSync(execContextId, execContext -> {
            if (execContext.state !=state.code) {
                execContext.setState(state.code);
                execContextCache.save(execContext);
            }
            return null;
        });
    }

    private void toStateWithCompletion(Long execContextId, EnumsApi.ExecContextState state) {
        execContextSyncService.getWithSync(execContextId, execContext -> {
            if (execContext.state !=state.code || execContext.completedOn==null) {
                execContext.setCompletedOn(System.currentTimeMillis());
                execContext.setState(state.code);
                execContextCache.save(execContext);
            }
            return null;
        });
    }

    public void toStarted(ExecContext execContext) {
        SourceCodeImpl sourceCode = sourceCodeCache.findById(execContext.getSourceCodeId());
        if (sourceCode == null) {
            toError(execContext.getId());
        }
        else {
            toStarted(execContext.getId());
        }
    }

    public void toStopped(Long execContextId) {
        toState(execContextId, EnumsApi.ExecContextState.STOPPED);
    }

    public void toStarted(Long execContextId) {
        toState(execContextId, EnumsApi.ExecContextState.STARTED);
    }

    public void toProduced(Long execContextId) {
        toState(execContextId, EnumsApi.ExecContextState.PRODUCED);
    }

    public void toFinished(Long execContextId) {
        toStateWithCompletion(execContextId, EnumsApi.ExecContextState.FINISHED);
    }

    public void toExportingToAtlas(Long execContextId) {
        toStateWithCompletion(execContextId, EnumsApi.ExecContextState.EXPORTING_TO_ATLAS);
    }

    public void toExportingToAtlasStarted(Long execContextId) {
        toStateWithCompletion(execContextId, EnumsApi.ExecContextState.EXPORTING_TO_ATLAS_WAS_STARTED);
    }

    public void toError(Long execContextId) {
        toStateWithCompletion(execContextId, EnumsApi.ExecContextState.ERROR);
    }


}

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

package ai.metaheuristic.ai.launchpad.exec_context;

import ai.metaheuristic.ai.launchpad.beans.SourceCodeImpl;
import ai.metaheuristic.ai.launchpad.source_code.SourceCodeCache;
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
@Profile("launchpad")
@Slf4j
@RequiredArgsConstructor
public class ExecContextFSM {

    private final ExecContextSyncService execContextSyncService;
    private final ExecContextCache execContextCache;
    private final SourceCodeCache sourceCodeCache;

    public void toState(Long workbookId, EnumsApi.ExecContextState state) {
        execContextSyncService.getWithSync(workbookId, workbook -> {
            if (workbook.execState!=state.code) {
                workbook.setExecState(state.code);
                execContextCache.save(workbook);
            }
            return null;
        });
    }

    private void toStateWithCompletion(Long workbookId, EnumsApi.ExecContextState state) {
        execContextSyncService.getWithSync(workbookId, workbook -> {
            if (workbook.execState!=state.code || workbook.completedOn==null) {
                workbook.setCompletedOn(System.currentTimeMillis());
                workbook.setExecState(state.code);
                execContextCache.save(workbook);
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

    public void toStopped(Long workbookId) {
        toState(workbookId, EnumsApi.ExecContextState.STOPPED);
    }

    public void toStarted(Long workbookId) {
        toState(workbookId, EnumsApi.ExecContextState.STARTED);
    }

    public void toProduced(Long workbookId) {
        toState(workbookId, EnumsApi.ExecContextState.PRODUCED);
    }

    public void toFinished(Long workbookId) {
        toStateWithCompletion(workbookId, EnumsApi.ExecContextState.FINISHED);
    }

    public void toExportingToAtlas(Long workbookId) {
        toStateWithCompletion(workbookId, EnumsApi.ExecContextState.EXPORTING_TO_ATLAS);
    }

    public void toExportingToAtlasStarted(Long workbookId) {
        toStateWithCompletion(workbookId, EnumsApi.ExecContextState.EXPORTING_TO_ATLAS_WAS_STARTED);
    }

    public void toError(Long workbookId) {
        toStateWithCompletion(workbookId, EnumsApi.ExecContextState.ERROR);
    }


}

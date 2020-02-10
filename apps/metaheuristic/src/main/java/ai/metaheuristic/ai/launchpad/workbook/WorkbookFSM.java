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

package ai.metaheuristic.ai.launchpad.workbook;

import ai.metaheuristic.ai.launchpad.beans.SourceCodeImpl;
import ai.metaheuristic.ai.launchpad.plan.PlanCache;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.launchpad.Workbook;
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
public class WorkbookFSM {

    private final WorkbookSyncService workbookSyncService;
    private final WorkbookCache workbookCache;
    private final PlanCache planCache;

    public void toState(Long workbookId, EnumsApi.WorkbookExecState state) {
        workbookSyncService.getWithSync(workbookId, workbook -> {
            if (workbook.execState!=state.code) {
                workbook.setExecState(state.code);
                workbookCache.save(workbook);
            }
            return null;
        });
    }

    private void toStateWithCompletion(Long workbookId, EnumsApi.WorkbookExecState state) {
        workbookSyncService.getWithSync(workbookId, workbook -> {
            if (workbook.execState!=state.code || workbook.completedOn==null) {
                workbook.setCompletedOn(System.currentTimeMillis());
                workbook.setExecState(state.code);
                workbookCache.save(workbook);
            }
            return null;
        });
    }

    public void toStarted(Workbook workbook) {
        SourceCodeImpl plan = planCache.findById(workbook.getPlanId());
        if (plan == null) {
            toError(workbook.getId());
        }
        else {
            toStarted(workbook.getId());
        }
    }

    public void toStopped(Long workbookId) {
        toState(workbookId, EnumsApi.WorkbookExecState.STOPPED);
    }

    public void toStarted(Long workbookId) {
        toState(workbookId, EnumsApi.WorkbookExecState.STARTED);
    }

    public void toProduced(Long workbookId) {
        toState(workbookId, EnumsApi.WorkbookExecState.PRODUCED);
    }

    public void toFinished(Long workbookId) {
        toStateWithCompletion(workbookId, EnumsApi.WorkbookExecState.FINISHED);
    }

    public void toExportingToAtlas(Long workbookId) {
        toStateWithCompletion(workbookId, EnumsApi.WorkbookExecState.EXPORTING_TO_ATLAS);
    }

    public void toExportingToAtlasStarted(Long workbookId) {
        toStateWithCompletion(workbookId, EnumsApi.WorkbookExecState.EXPORTING_TO_ATLAS_WAS_STARTED);
    }

    public void toError(Long workbookId) {
        toStateWithCompletion(workbookId, EnumsApi.WorkbookExecState.ERROR);
    }


}

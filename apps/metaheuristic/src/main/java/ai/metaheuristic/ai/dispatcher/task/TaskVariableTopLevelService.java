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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.event.SetVariableReceivedEvent;
import ai.metaheuristic.ai.dispatcher.southbridge.UploadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 12/21/2020
 * Time: 5:32 AM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class TaskVariableTopLevelService {

    public static final UploadResult OK_UPLOAD_RESULT = new UploadResult(Enums.UploadVariableStatus.OK);

    private final TaskVariableService taskVariableService;

    @Async
    @EventListener
    public void setVariableReceived(SetVariableReceivedEvent event) {
        try {
            log.debug("call TaskVariableTopLevelService.setVariableReceived({},{}, {})", event.variableId, event.variableId, event.nullified);
            try {
                TaskSyncService.getWithSync(event.taskId,
                        () -> updateStatusOfVariable(event.taskId, event.variableId, event.nullified));

            } catch (TaskVariableService.UpdateStatusOfVariableException e) {
                log.error("{}, status: {}", e.uploadResult.error, e.uploadResult.status);
            }
        } catch (Throwable th) {
            log.error("Error, need to investigate ", th);
        }
    }

    private Void updateStatusOfVariable(Long taskId, Long variableId, boolean nullified) {
        return taskVariableService.updateStatusOfVariable(taskId, variableId, nullified);
    }

    public UploadResult updateStatusOfVariable(Long taskId, Long variableId) {
        try {
            taskVariableService.updateStatusOfVariable(taskId, variableId, false);
            return OK_UPLOAD_RESULT;
        } catch( TaskVariableService.UpdateStatusOfVariableException e) {
            return e.uploadResult;
        }
    }

}

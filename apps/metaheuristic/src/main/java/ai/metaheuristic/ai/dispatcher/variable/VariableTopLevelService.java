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

package ai.metaheuristic.ai.dispatcher.variable;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.southbridge.UploadResult;
import ai.metaheuristic.ai.dispatcher.task.TaskTransactionalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * @author Serge
 * Date: 9/28/2020
 * Time: 11:00 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class VariableTopLevelService {

    private static final UploadResult OK_UPLOAD_RESULT = new UploadResult(Enums.UploadResourceStatus.OK, null);

    private final VariableService variableService;
    private final TaskTransactionalService taskTransactionalService;
    private final ExecContextSyncService execContextSyncService;

    @Transactional
    public UploadResult storeVariable(InputStream variableIS, long length, TaskImpl task, Variable variable) {
        execContextSyncService.checkWriteLockPresent(task.execContextId);
        try {
            variableService.update(variableIS, length, variable);
            Enums.UploadResourceStatus status = taskTransactionalService.setResultReceived(task, variable.getId());
            return status== Enums.UploadResourceStatus.OK
                    ? OK_UPLOAD_RESULT
                    : new UploadResult(status, "#490.020 can't update resultReceived field for task #"+ variable.getId()+"");
        }
        catch (PessimisticLockingFailureException th) {
            final String es = "#490.040 can't store the result, need to try again. Error: " + th.toString();
            log.error(es, th);
            return new UploadResult(Enums.UploadResourceStatus.PROBLEM_WITH_LOCKING, es);
        }
        catch (Throwable th) {
            final String error = "#490.060 can't store the result, Error: " + th.toString();
            log.error(error, th);
            return new UploadResult(Enums.UploadResourceStatus.GENERAL_ERROR, error);
        }
    }
}

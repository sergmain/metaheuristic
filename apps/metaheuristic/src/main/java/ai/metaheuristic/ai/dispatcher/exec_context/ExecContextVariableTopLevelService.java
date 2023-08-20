/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.event.events.TaskCommunicationEvent;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.southbridge.UploadResult;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.dispatcher.task.TaskVariableTopLevelService;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.exceptions.VariableCommonException;
import ai.metaheuristic.ai.exceptions.VariableSavingException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.commons.utils.DirUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static ai.metaheuristic.ai.dispatcher.task.TaskVariableTopLevelService.OK_UPLOAD_RESULT;

/**
 * @author Serge
 * Date: 10/8/2020
 * Time: 7:19 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecContextVariableTopLevelService {

    private final TaskRepository taskRepository;
    private final TaskVariableTopLevelService taskVariableTopLevelService;
    private final VariableTxService variableService;
    private final VariableRepository variableRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UploadResult setVariableAsNull(@Nullable Long taskId, @Nullable Long variableId) {
        TxUtils.checkTxNotExists();

        if (taskId==null) {
            return new UploadResult(Enums.UploadVariableStatus.UNRECOVERABLE_ERROR,"#440.020 taskId is null" );
        }
        if (variableId==null) {
            return new UploadResult(Enums.UploadVariableStatus.UNRECOVERABLE_ERROR,"#440.040 variableId is null" );
        }
        Long execContextId = taskRepository.getExecContextId(taskId);
        if (execContextId==null) {
            final String es = "#440.060 Task "+taskId+" is obsolete and was already deleted";
            log.warn(es);
            return new UploadResult(Enums.UploadVariableStatus.TASK_NOT_FOUND, es);
        }

        eventPublisher.publishEvent(new TaskCommunicationEvent(taskId));
        try {
            VariableSyncService.getWithSyncVoid(variableId,
                    () -> variableService.setVariableAsNull(taskId, variableId));
            return OK_UPLOAD_RESULT;
        }
        catch (ObjectOptimisticLockingFailureException th) {
            if (log.isDebugEnabled()) {
                TaskImpl t = taskRepository.findByIdReadOnly(taskId);
                if (t==null) {
                    log.debug("#440.080 uploadVariable(), task #{} wasn't found", taskId);
                }
                else {
                    log.debug("#440.100 uploadVariable(), task id: #{}, ver: {}, task: {}", t.id, t.version, t);
                }
            }
            final String es = "#440.120 can't store the result, need to try again. Error: " + th.getMessage();
            log.error(es);
            return new UploadResult(Enums.UploadVariableStatus.PROBLEM_WITH_LOCKING, es);
        }
        catch (VariableCommonException th) {
            final String es = "#440.140 can't store the result, unrecoverable error with data. Error: " + th.getMessage();
            log.error(es, th);
            return new UploadResult(Enums.UploadVariableStatus.UNRECOVERABLE_ERROR, es);
        }
        catch (Throwable th) {
            final String error = "#440.160 can't store the result, Error: " + th.getMessage();
            log.error(error, th);
            return new UploadResult(Enums.UploadVariableStatus.GENERAL_ERROR, error);
        }
    }

    public UploadResult uploadVariable(@Nullable MultipartFile file, @Nullable Long taskId, @Nullable Long variableId) {
        TxUtils.checkTxNotExists();

        if (file==null) {
            return new UploadResult(Enums.UploadVariableStatus.UNRECOVERABLE_ERROR, "#440.180 file in null");
        }
        if (file.getSize()==0) {
            return new UploadResult(Enums.UploadVariableStatus.UNRECOVERABLE_ERROR, "#440.182 file has zero length");
        }
        String originFilename = file.getOriginalFilename();
        if (StringUtils.isBlank(originFilename)) {
            return new UploadResult(Enums.UploadVariableStatus.FILENAME_IS_BLANK, "#440.200 name of uploaded file is blank");
        }
        if (taskId==null) {
            return new UploadResult(Enums.UploadVariableStatus.UNRECOVERABLE_ERROR,"#440.220 taskId is null" );
        }
        if (variableId==null) {
            return new UploadResult(Enums.UploadVariableStatus.UNRECOVERABLE_ERROR,"#440.240 variableId is null" );
        }
        Long execContextId = taskRepository.getExecContextId(taskId);
        if (execContextId==null) {
            final String es = "#440.260 Task "+taskId+" is obsolete and was already deleted";
            log.warn(es);
            return new UploadResult(Enums.UploadVariableStatus.TASK_NOT_FOUND, es);
        }

        eventPublisher.publishEvent(new TaskCommunicationEvent(taskId));

        Path tempDir=null;
        try {
            tempDir = DirUtils.createMhTempPath("upload-variable-");
            if (tempDir==null || !Files.isDirectory(tempDir)) {
                final String location = System.getProperty("java.io.tmpdir");
                return new UploadResult(Enums.UploadVariableStatus.GENERAL_ERROR, "#440.280 can't create temporary directory in " + location);
            }

            UploadResult uploadResult = VariableSyncService.getWithSync(variableId, () -> {
                Variable v = variableRepository.findByIdAsSimple(variableId);
                if (v == null) {
                    return new UploadResult(Enums.UploadVariableStatus.VARIABLE_NOT_FOUND, "#440.285 variable #" + variableId + " wasn't found");
                }
                if (v.inited) {
                    return OK_UPLOAD_RESULT;
                }
                try (final InputStream is = file.getInputStream(); BufferedInputStream bis = new BufferedInputStream(is, 0x8000)) {
                    variableService.storeVariable(bis, file.getSize(), execContextId, taskId, variableId);
                    return OK_UPLOAD_RESULT;
                } catch (Throwable th) {
                    final String error = "#440.290 can't store the result, Error: " + th.getMessage();
                    log.error(error, th);
                    return new UploadResult(Enums.UploadVariableStatus.GENERAL_ERROR, error);
                }
            });

            if (uploadResult.status!= Enums.UploadVariableStatus.OK) {
                return uploadResult;
            }
            try {
                uploadResult = TaskSyncService.getWithSync(taskId, () -> taskVariableTopLevelService.updateStatusOfVariable(taskId, variableId));
            }
            catch (ObjectOptimisticLockingFailureException th) {
                log.warn("#440.295 ObjectOptimisticLockingFailureException while updating the status of variable #{}, will try again", variableId);
                uploadResult = TaskSyncService.getWithSync(taskId, () -> taskVariableTopLevelService.updateStatusOfVariable(taskId, variableId));
            }
            if (log.isDebugEnabled()) {
                TaskImpl t = taskRepository.findByIdReadOnly(taskId);
                if (t==null) {
                    log.debug("#440.300 uploadVariable(), task #{} wasn't found", taskId);
                }
                else {
                    log.debug("#440.320 uploadVariable(), task id: #{}, ver: {}, task: {}", t.id, t.version, t);
                }
            }
            return uploadResult;
        }
        catch (ObjectOptimisticLockingFailureException th) {
            if (log.isDebugEnabled()) {
                TaskImpl t = taskRepository.findByIdReadOnly(taskId);
                if (t==null) {
                    log.debug("#440.340 uploadVariable(), task #{} wasn't found", taskId);
                }
                else {
                    log.debug("#440.360 uploadVariable(), task id: #{}, ver: {}, task: {}", t.id, t.version, t);
                }
            }
            final String es = "#440.380 can't store the result, need to try again. Error: " + th.getMessage();
            log.error(es, th);
            return new UploadResult(Enums.UploadVariableStatus.PROBLEM_WITH_LOCKING, es);
        }
        catch (VariableSavingException th) {
            final String es = "#440.400 can't store the result, unrecoverable error with data. Error: " + th.getMessage();
            log.error(es, th);
            return new UploadResult(Enums.UploadVariableStatus.UNRECOVERABLE_ERROR, es);
        }
        catch (Throwable th) {
            final String error = "#440.420 can't store the result, Error: " + th.getMessage();
            log.error(error, th);
            return new UploadResult(Enums.UploadVariableStatus.GENERAL_ERROR, error);
        }
        finally {
            DirUtils.deletePathAsync(tempDir);
        }

    }

    /**
     * return string as "true"/"false".
     *      "true" - if variable already inited
     *      "false" - variable isn't inited yet
     *      "null" - variable doesn't exist;
     *
     * @param variableId Long
     * @return String
     */
    public String getVariableStatus(Long variableId) {
        return VariableSyncService.getWithSync(variableId,
                () -> {
                    Variable v = variableRepository.findByIdAsSimple(variableId);
                    return v == null ? "null" : String.valueOf(v.inited);
                });
    }
}

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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.DispatcherCommandProcessor;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.function.FunctionDataService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.southbridge.UploadResult;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableService;
import ai.metaheuristic.ai.exceptions.VariableSavingException;
import ai.metaheuristic.commons.utils.DirUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;

/**
 * @author Serge
 * Date: 10/8/2020
 * Time: 7:19 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextVariableTopLevelService {

    private final Globals globals;
    private final VariableService variableService;
    private final GlobalVariableService globalVariableService;
    private final FunctionDataService functionDataService;
    private final DispatcherCommandProcessor dispatcherCommandProcessor;
    private final ExecContextRepository execContextRepository;
    private final ProcessorTopLevelService processorTopLevelService;
    private final TaskRepository taskRepository;
    private final ExecContextSyncService execContextSyncService;
    private final ExecContextVariableService execContextVariableService;

    public UploadResult setVariableAsNull(@Nullable Long taskId, @Nullable Long variableId) {
        if (taskId==null) {
            return new UploadResult(Enums.UploadVariableStatus.UNRECOVERABLE_ERROR,"#440.260 taskId is null" );
        }
        if (variableId==null) {
            return new UploadResult(Enums.UploadVariableStatus.UNRECOVERABLE_ERROR,"#440.280 variableId is null" );
        }
        Long execContextId = taskRepository.getExecContextId(taskId);
        if (execContextId==null) {
            final String es = "#440.005 Task "+taskId+" is obsolete and was already deleted";
            log.warn(es);
            return new UploadResult(Enums.UploadVariableStatus.TASK_NOT_FOUND, es);
        }

        try {
            final UploadResult uploadResult = execContextSyncService.getWithSync(execContextId,
                    () -> execContextVariableService.setVariableAsNull(taskId, variableId));
            return uploadResult;
        }
        catch (ObjectOptimisticLockingFailureException th) {
            if (log.isDebugEnabled()) {
                TaskImpl t = taskRepository.findById(taskId).orElse(null);
                if (t==null) {
                    log.debug("#440.047 uploadVariable(), task #{} wasn't found", taskId);
                }
                else {
                    log.debug("#440.048 uploadVariable(), task id: #{}, ver: {}, task: {}", t.id, t.version, t);
                }
            }
            final String es = "#440.075 can't store the result, need to try again. Error: " + th.toString();
            log.error(es, th);
            return new UploadResult(Enums.UploadVariableStatus.PROBLEM_WITH_LOCKING, es);
        }
        catch (VariableSavingException th) {
            final String es = "#440.080 can't store the result, unrecoverable error with data. Error: " + th.toString();
            log.error(es, th);
            return new UploadResult(Enums.UploadVariableStatus.UNRECOVERABLE_ERROR, es);
        }
        catch (Throwable th) {
            final String error = "#440.090 can't store the result, Error: " + th.toString();
            log.error(error, th);
            return new UploadResult(Enums.UploadVariableStatus.GENERAL_ERROR, error);
        }
    }

    public UploadResult uploadVariable(@Nullable MultipartFile file, @Nullable Long taskId, @Nullable Long variableId) {
        if (file==null) {
            return new UploadResult(Enums.UploadVariableStatus.UNRECOVERABLE_ERROR, "#440.015 file in null");
        }
        String originFilename = file.getOriginalFilename();
        if (StringUtils.isBlank(originFilename)) {
            return new UploadResult(Enums.UploadVariableStatus.FILENAME_IS_BLANK, "#440.020 name of uploaded file is blank");
        }
        if (taskId==null) {
            return new UploadResult(Enums.UploadVariableStatus.UNRECOVERABLE_ERROR,"#440.040 taskId is null" );
        }
        if (variableId==null) {
            return new UploadResult(Enums.UploadVariableStatus.UNRECOVERABLE_ERROR,"#440.060 variableId is null" );
        }
        Long execContextId = taskRepository.getExecContextId(taskId);
        if (execContextId==null) {
            final String es = "#440.080 Task "+taskId+" is obsolete and was already deleted";
            log.warn(es);
            return new UploadResult(Enums.UploadVariableStatus.TASK_NOT_FOUND, es);
        }

        File tempDir=null;
        final File variableFile;
        try {
            tempDir = DirUtils.createTempDir("upload-variable-");
            if (tempDir==null || tempDir.isFile()) {
                final String location = System.getProperty("java.io.tmpdir");
                return new UploadResult(Enums.UploadVariableStatus.GENERAL_ERROR, "#440.100 can't create temporary directory in " + location);
            }
            variableFile = new File(tempDir, "variable.");
            log.debug("Start storing an uploaded resource data to disk, target file: {}", variableFile.getPath());
            try(OutputStream os = new FileOutputStream(variableFile)) {
                IOUtils.copy(file.getInputStream(), os, 64000);
            }
            UploadResult uploadResult;
            try (InputStream is = new FileInputStream(variableFile)) {
                uploadResult = execContextVariableService.storeVariable(is, variableFile.length(), execContextId, taskId, variableId);
            }
            if (uploadResult.status!= Enums.UploadVariableStatus.OK) {
                return uploadResult;
            }
            uploadResult = execContextSyncService.getWithSync(execContextId, () -> execContextVariableService.updateStatusOfVariable(taskId, variableId));

            if (log.isDebugEnabled()) {
                TaskImpl t = taskRepository.findById(taskId).orElse(null);
                if (t==null) {
                    log.debug("#440.120 uploadVariable(), task #{} wasn't found", taskId);
                }
                else {
                    log.debug("#440.140 uploadVariable(), task id: #{}, ver: {}, task: {}", t.id, t.version, t);
                }
            }
            return uploadResult;
        }
        catch (ObjectOptimisticLockingFailureException th) {
            if (log.isDebugEnabled()) {
                TaskImpl t = taskRepository.findById(taskId).orElse(null);
                if (t==null) {
                    log.debug("#440.160 uploadVariable(), task #{} wasn't found", taskId);
                }
                else {
                    log.debug("#440.180 uploadVariable(), task id: #{}, ver: {}, task: {}", t.id, t.version, t);
                }
            }
            final String es = "#440.200 can't store the result, need to try again. Error: " + th.toString();
            log.error(es, th);
            return new UploadResult(Enums.UploadVariableStatus.PROBLEM_WITH_LOCKING, es);
        }
        catch (VariableSavingException th) {
            final String es = "#440.220 can't store the result, unrecoverable error with data. Error: " + th.toString();
            log.error(es, th);
            return new UploadResult(Enums.UploadVariableStatus.UNRECOVERABLE_ERROR, es);
        }
        catch (Throwable th) {
            final String error = "#440.240 can't store the result, Error: " + th.toString();
            log.error(error, th);
            return new UploadResult(Enums.UploadVariableStatus.GENERAL_ERROR, error);
        }
        finally {
            DirUtils.deleteAsync(tempDir);
        }

    }


}

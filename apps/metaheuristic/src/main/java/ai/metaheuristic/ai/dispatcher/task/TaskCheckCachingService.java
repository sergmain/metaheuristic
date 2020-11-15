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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.CacheProcess;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.cache.CacheService;
import ai.metaheuristic.ai.dispatcher.cache.CacheVariableService;
import ai.metaheuristic.ai.dispatcher.commons.DataHolder;
import ai.metaheuristic.ai.dispatcher.data.CacheData;
import ai.metaheuristic.ai.dispatcher.event.CheckTaskCanBeFinishedEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTaskFinishingService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTaskStateService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextVariableService;
import ai.metaheuristic.ai.dispatcher.repositories.CacheProcessRepository;
import ai.metaheuristic.ai.dispatcher.repositories.CacheVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.InvalidateCacheProcessException;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.util.List;

/**
 * @author Serge
 * Date: 10/30/2020
 * Time: 7:25 PM
 */
@SuppressWarnings("DuplicatedCode")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskCheckCachingService {

    private final Globals globals;
    private final ExecContextService execContextService;
    private final TaskRepository taskRepository;
    private final ExecContextTaskStateService execContextTaskStateService;
    private final ExecContextTaskFinishingService execContextTaskFinishingService;
    private final VariableService variableService;
    private final CacheService cacheService;
    private final CacheVariableService cacheVariableService;
    private final CacheProcessRepository cacheProcessRepository;
    private final CacheVariableRepository cacheVariableRepository;
    private final ExecContextVariableService execContextVariableService;

    @Data
    @AllArgsConstructor
    private static class StoredVariable {
        public Long id;
        public String name;
        public boolean nullified;
    }

    @Transactional
    public Void invalidateAndSetToNone(Long execContextId, Long taskId, Long cacheProcessId) {
        ExecContextImpl execContext = execContextService.findById(execContextId);
        if (execContext==null) {
            log.info("#609.020 ExecContext #{} doesn't exists", execContextId);
            return null;
        }
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            return null;
        }

        cacheVariableRepository.deleteByCacheProcessId(cacheProcessId);
        cacheProcessRepository.deleteById(cacheProcessId);
        execContextTaskStateService.updateTaskExecStates(execContext, task, EnumsApi.TaskExecState.NONE, null);

        return null;
    }

    @Transactional
    public Void checkCaching(Long execContextId, Long taskId, DataHolder holder) {

        ExecContextImpl execContext = execContextService.findById(execContextId);
        if (execContext==null) {
            log.info("#609.020 ExecContext #{} doesn't exists", execContextId);
            return null;
        }

        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            return null;
        }

        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);

        CacheData.Key fullKey = cacheService.getKey(tpy);

        String keyAsStr = fullKey.asString();
        byte[] bytes = keyAsStr.getBytes();

        CacheProcess cacheProcess=null;
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            String sha256 = Checksum.getChecksum(EnumsApi.HashAlgo.SHA256, is);
            String key = new CacheData.Sha256PlusLength(sha256, keyAsStr.length()).asString();
            cacheProcess = cacheProcessRepository.findByKeySha256Length(key);
        } catch (IOException e) {
            log.error("#609.040 Error while preparing a cache key, task will be processed without cached data", e);
        }

        if (cacheProcess!=null) {
            log.info("#609.060 cached data was found for task #{}, variables will be copied and will task be set as FINISHED", taskId);
            // finish task with cached data

            List<Object[]> vars = cacheVariableRepository.getIdsByCacheProcessId(cacheProcess.id);
            if (vars.size()!=tpy.task.outputs.size()) {
                log.warn("#609.080 cashProcess #{} is broken. Number of stored variable is {} but expected {}. CacheProcess will be invalidated", cacheProcess.id, vars.size(), tpy.task.outputs.size());
                throw new InvalidateCacheProcessException(execContextId, taskId, cacheProcess.id);
            }
            for (TaskParamsYaml.OutputVariable output : tpy.task.outputs) {
                Object[] obj = vars.stream().filter(o->o[1].equals(output.name)).findFirst().orElse(null);
                if (obj==null) {
                    log.warn("#609.100 cashProcess #{} is broken. Number of stored variable is {} but expected {}. CacheProcess will be invalidated", cacheProcess.id, vars.size(), tpy.task.outputs.size());
                    throw new InvalidateCacheProcessException(execContextId, taskId, cacheProcess.id);
                }
            }

            for (TaskParamsYaml.OutputVariable output : tpy.task.outputs) {
                Object[] obj = vars.stream().filter(o->o[1].equals(output.name)).findFirst().orElse(null);
                if (obj==null) {
                    throw new IllegalStateException("#609.120 ???? How???");
                }
                try {
                    StoredVariable storedVariable = new StoredVariable( ((Number)obj[0]).longValue(), (String)obj[1], Boolean.TRUE.equals(obj[2]));
                    if (storedVariable.nullified) {
                        execContextVariableService.setVariableAsNull(taskId, output.id, holder);
                    }
                    else {
                        final File tempFile = File.createTempFile("var-" + obj[0] + "-", ".bin", globals.dispatcherTempDir);
                        holder.files.add(tempFile);

                        cacheVariableService.storeToFile(storedVariable.id, tempFile);

                        InputStream is = new FileInputStream(tempFile);
                        holder.inputStreams.add(is);
                        variableService.storeData(is, tempFile.length(), output.id, output.filename);
                    }
                    Enums.UploadVariableStatus status = execContextVariableService.setVariableReceived(task, output.id);
                    if (status!= Enums.UploadVariableStatus.OK) {
                        log.error("#609.155 error while setting variable was received, status: {}", status);
                        throw new InvalidateCacheProcessException(execContextId, taskId, cacheProcess.id);
                    }

                } catch (IOException e) {
                    log.warn("#609.160 error", e);
                    throw new InvalidateCacheProcessException(execContextId, taskId, cacheProcess.id);
                }
            }
            FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec();
            functionExec.exec = new FunctionApiData.SystemExecResult(tpy.task.function.code, true, 0,
                    "Process was finished with cached data, cacheProcessId: "+ cacheProcess.id);

            task.setFunctionExecResults(FunctionExecUtils.toString(functionExec));
            task.setResultReceived(true);

            holder.events.add(new CheckTaskCanBeFinishedEvent(task.execContextId, task.id, false));
        }
        else {
            log.info("#609.080 cached data wasn't found for task #{}", taskId);
            execContextTaskStateService.updateTaskExecStates(execContext, task, EnumsApi.TaskExecState.NONE, null);
        }
        return null;
    }

}

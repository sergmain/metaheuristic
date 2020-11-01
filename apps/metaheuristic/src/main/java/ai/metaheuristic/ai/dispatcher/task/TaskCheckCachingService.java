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

import ai.metaheuristic.ai.dispatcher.beans.CacheProcess;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.CacheData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTaskStateService;
import ai.metaheuristic.ai.dispatcher.repositories.CacheProcessRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.CountingInputStream;
import org.apache.commons.io.IOUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;

/**
 * @author Serge
 * Date: 10/30/2020
 * Time: 7:25 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskCheckCachingService {

    private final ExecContextService execContextService;
    private final TaskRepository taskRepository;
    private final ExecContextTaskStateService execContextTaskStateService;
    private final VariableService variableService;
    private final CacheProcessRepository cacheProcessRepository;

    @Transactional
    public Void checkCaching(Long execContextId, Long taskId) {

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

        CacheData.Key fullKey = new CacheData.Key(tpy.task.function.code);
        if (tpy.task.inline!=null) {
            fullKey.inline.putAll(tpy.task.inline);
        }
        tpy.task.inputs.sort(Comparator.comparingLong(o -> o.id));

        for (TaskParamsYaml.InputVariable input : tpy.task.inputs) {
            if (input.context== EnumsApi.VariableContext.array) {
                String data = variableService.getVariableDataAsString(input.id);
                VariableArrayParamsYaml vapy = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.to(data);
                for (VariableArrayParamsYaml.Variable variable : vapy.array) {
                    fullKey.inputs.add(variableService.getSha256Length(Long.parseLong(variable.id)));
                }
            }
            else {
                fullKey.inputs.add(variableService.getSha256Length(input.id));
            }
        }
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

            // TODO need to be changed to set finished()
            execContextTaskStateService.updateTaskExecStates(execContext, task, EnumsApi.TaskExecState.NONE, null);
        }
        else {
            log.info("#609.080 cached data wasn't found for task #{}", taskId);
            execContextTaskStateService.updateTaskExecStates(execContext, task, EnumsApi.TaskExecState.NONE, null);
        }
        return null;
    }
}

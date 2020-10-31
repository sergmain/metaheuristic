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
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        CacheData.Key key = new CacheData.Key(tpy.task.function.code);
        if (tpy.task.inline!=null) {
            key.inline.putAll(tpy.task.inline);
        }
        tpy.task.inputs.stream()
                .map(v->v.id)
                .sorted(Comparator.naturalOrder())
                .map(variableService::getSha256Length)
                .forEach(key.inputs::add);

        String keyAsStr = key.asString();
        CacheProcess cacheProcess = cacheProcessRepository.findByKeySha256Length(keyAsStr);

        if (cacheProcess!=null) {
            // finish task with cached data


            // TODO need to be changed to set finished()
            execContextTaskStateService.updateTaskExecStates(execContext, task, EnumsApi.TaskExecState.NONE, null);
        }
        else {
            execContextTaskStateService.updateTaskExecStates(execContext, task, EnumsApi.TaskExecState.NONE, null);
        }
        return null;
    }
}

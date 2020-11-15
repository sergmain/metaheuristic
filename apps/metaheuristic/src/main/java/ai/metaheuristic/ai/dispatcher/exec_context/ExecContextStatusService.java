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

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.event.TaskCreatedEvent;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.utils.JsonUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 10/27/2020
 * Time: 3:51 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextStatusService {

    private final ExecContextRepository execContextRepository;
    private final ExecContextSyncService execContextSyncService;
    private final ExecContextCache execContextCache;

    private DispatcherCommParamsYaml.ExecContextStatus cachedStatus = null;
    private long updatedOn = 0L;
    private static final long TTL_FOR_STATUS = TimeUnit.SECONDS.toMillis(10);

    public synchronized DispatcherCommParamsYaml.ExecContextStatus getExecContextStatuses() {
        if (cachedStatus==null) {
            resetStatus();
        }
        if (System.currentTimeMillis() - updatedOn > TTL_FOR_STATUS) {
            resetStatus();
        }
        return cachedStatus;
    }

    private void resetStatus() {
        cachedStatus = new DispatcherCommParamsYaml.ExecContextStatus(
                execContextRepository.findAllExecStates()
                        .stream()
                        .map(o -> toSimpleStatus((Long)o[0], (Integer)o[1]))
                        .collect(Collectors.toList()));
        updatedOn = System.currentTimeMillis();
    }

    private static DispatcherCommParamsYaml.ExecContextStatus.SimpleStatus toSimpleStatus(Long execContextId, Integer execSate) {
        return new DispatcherCommParamsYaml.ExecContextStatus.SimpleStatus(execContextId, EnumsApi.ExecContextState.toState(execSate));
    }

    @Transactional
    @SneakyThrows
    public Void registerCreatedTask(TaskCreatedEvent event) {
        execContextSyncService.checkWriteLockPresent(event.taskVariablesInfo.execContextId);

        ExecContextImpl execContext = execContextCache.findById(event.taskVariablesInfo.execContextId);
        if (execContext==null) {
            return null;
        }
        ExecContextParamsYaml ecpy = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(execContext.params);
        ExecContextApiData.ExecContextTasksStatesInfo info = ExecContextUtils.getExecContextTasksVariablesInfo(ecpy.tasksVariablesInfo);

        boolean isNew = true;
        for (ExecContextApiData.TaskStateInfo task : info.tasks) {
            if (task.taskId.equals(event.taskVariablesInfo.taskId)) {
                isNew = false;
                if (task.inputs !=null && !task.inputs.isEmpty()) {
                    task.inputs.clear();
                }
                if (task.outputs !=null && !task.outputs.isEmpty()) {
                    task.outputs.clear();
                }
                task.inputs = event.taskVariablesInfo.inputs;
                task.outputs = event.taskVariablesInfo.outputs;
                break;
            }
        }
        if (isNew) {
            info.tasks.add(event.taskVariablesInfo);
        }
        ecpy.tasksVariablesInfo = JsonUtils.getMapper().writeValueAsString(info);

        execContext.params = ExecContextParamsYamlUtils.BASE_YAML_UTILS.toString(ecpy);

        return null;
    }

}

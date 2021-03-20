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

package ai.metaheuristic.ai.dispatcher.exec_context_variable_state;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextVariableState;
import ai.metaheuristic.ai.dispatcher.event.CheckTaskCanBeFinishedTxEvent;
import ai.metaheuristic.ai.dispatcher.event.TaskCreatedEvent;
import ai.metaheuristic.ai.dispatcher.event.VariableUploadedEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextTaskStateRepository;
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYaml;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;

/**
 * @author Serge
 * Date: 3/19/2021
 * Time: 11:20 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextVariableStateService {

    private final ExecContextTaskStateRepository execContextTaskStateRepository;
    private final ExecContextVariableStateSyncService execContextVariableStateSyncService;
    private final ExecContextCache execContextCache;
    private final ExecContextVariableStateCache execContextVariableStateCache;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Void registerVariableState(Long execContextTaskStateId, VariableUploadedEvent event) {
        eventPublisher.publishEvent(new CheckTaskCanBeFinishedTxEvent(event.execContextId, event.taskId));
        registerVariableStateInternal(execContextTaskStateId, event);
        return null;
    }

    private Void registerVariableStateInternal(Long execContextTaskStateId, VariableUploadedEvent event) {
        register(execContextTaskStateId, (ecpy)-> {
            for (ExecContextApiData.TaskStateInfo task : ecpy.tasks) {
                if (task.taskId.equals(event.taskId)) {
                    if (task.outputs==null || task.outputs.isEmpty()) {
                        log.warn(" (task.outputs==null || task.outputs.isEmpty()) can't process event {}", event);
                    }
                    else {
                        for (ExecContextApiData.VariableInfo output : task.outputs) {
                            if (output.id.equals(event.variableId)) {
                                output.inited = true;
                                output.nullified = event.nullified;
                            }
                        }
                    }
                    break;
                }
            }
        });
        return null;
    }

    @Transactional
    public Void registerCreatedTask(TaskCreatedEvent event) {
        register(event.taskVariablesInfo.execContextId, (ecpy)-> {
            boolean isNew = true;
            for (ExecContextApiData.TaskStateInfo task : ecpy.tasks) {
                if (task.taskId.equals(event.taskVariablesInfo.taskId)) {
                    isNew = false;
                    if (task.inputs != null && !task.inputs.isEmpty()) {
                        task.inputs.clear();
                    }
                    if (task.outputs != null && !task.outputs.isEmpty()) {
                        task.outputs.clear();
                    }
                    task.inputs = event.taskVariablesInfo.inputs;
                    task.outputs = event.taskVariablesInfo.outputs;
                    break;
                }
            }
            if (isNew) {
                ecpy.tasks.add(event.taskVariablesInfo);
            }
        });
        return null;
    }

    private Void register(Long execContextTaskStateId, Consumer<ExecContextApiData.ExecContextTasksStatesInfo> supplier) {
        execContextVariableStateSyncService.checkWriteLockPresent(execContextTaskStateId);

        ExecContextVariableState execContextVariableState = execContextVariableStateCache.findById(execContextTaskStateId);
        if (execContextVariableState==null) {
            log.warn("#211.120 ExecContext #{} wasn't found", execContextTaskStateId);
            return null;
        }
        ExecContextApiData.ExecContextTasksStatesInfo ecpy = execContextVariableState.getExecContextVariableStateInfo();
        supplier.accept(ecpy);
        execContextVariableState.updateParams(ecpy);

        return null;
    }

    @Transactional
    public Long initExecContextTaskState(Long execContextId) {
        ExecContextTaskState bean = new ExecContextTaskState();
        bean.updateParams(new ExecContextTaskStateParamsYaml());
        bean.execContextId = execContextId;
        bean = execContextTaskStateRepository.save(bean);
        return bean.id;
    }
}

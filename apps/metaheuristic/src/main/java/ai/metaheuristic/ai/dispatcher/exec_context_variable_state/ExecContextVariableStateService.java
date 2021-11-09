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

import ai.metaheuristic.ai.dispatcher.beans.ExecContextVariableState;
import ai.metaheuristic.ai.dispatcher.event.CheckTaskCanBeFinishedTxEvent;
import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.VariableUploadedEvent;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextVariableStateRepository;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    private final ExecContextVariableStateCache execContextVariableStateCache;
    private final ExecContextVariableStateRepository execContextVariableStateRepository;
    private final EventPublisherService eventPublisherService;

    @Transactional
    public Void registerVariableStates(Long execContextId, Long execContextVariableStateId, List<VariableUploadedEvent> event) {
        registerVariableStateInternal(execContextId, execContextVariableStateId, event);
        return null;
    }

    private Void registerVariableStateInternal(Long execContextId, Long execContextVariableStateId, List<VariableUploadedEvent> events) {
        register(execContextVariableStateId, (ecpy)-> {
            for (VariableUploadedEvent event : events) {
                eventPublisherService.publishCheckTaskCanBeFinishedTxEvent(new CheckTaskCanBeFinishedTxEvent(execContextId, event.taskId));
                for (ExecContextApiData.VariableState task : ecpy.tasks) {
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
            }
        });
        return null;
    }

    @Transactional
    public Void registerCreatedTasks(Long execContextVariableStateId, List<ExecContextApiData.VariableState> events) {
        register(execContextVariableStateId, (ecpy)-> {
            for (ExecContextApiData.VariableState event : events) {
                boolean isNew = true;
                for (ExecContextApiData.VariableState task : ecpy.tasks) {
                    if (task.taskId.equals(event.taskId)) {
                        isNew = false;
                        if (task.inputs != null && !task.inputs.isEmpty()) {
                            task.inputs.clear();
                        }
                        if (task.outputs != null && !task.outputs.isEmpty()) {
                            task.outputs.clear();
                        }
                        task.inputs = event.inputs;
                        task.outputs = event.outputs;
                        break;
                    }
                }
                if (isNew) {
                    ecpy.tasks.add(event);
                }
            }
        });
        return null;
    }

    private Void register(Long execContextVariableStateId, Consumer<ExecContextApiData.ExecContextVariableStates> supplier) {
        ExecContextVariableStateSyncService.checkWriteLockPresent(execContextVariableStateId);

        ExecContextVariableState execContextVariableState = execContextVariableStateCache.findById(execContextVariableStateId);
        if (execContextVariableState==null) {
            log.warn("#211.120 ExecContext #{} wasn't found", execContextVariableStateId);
            return null;
        }
        ExecContextApiData.ExecContextVariableStates ecpy = execContextVariableState.getExecContextVariableStateInfo();
        supplier.accept(ecpy);
        execContextVariableState.updateParams(ecpy);

        return null;
    }

    @Transactional
    public Void deleteOrphanVariableStates(List<Long> ids) {
        execContextVariableStateRepository.deleteAllByIdIn(ids);
        return null;
    }

}

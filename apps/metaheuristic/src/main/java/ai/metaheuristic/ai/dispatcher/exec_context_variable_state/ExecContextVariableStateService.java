/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.events.CheckTaskCanBeFinishedTxEvent;
import ai.metaheuristic.ai.dispatcher.event.events.VariableUploadedEvent;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextVariableStateRepository;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author Serge
 * Date: 3/19/2021
 * Time: 11:20 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecContextVariableStateService {

    private final ExecContextVariableStateTxService execContextVariableStateCache;
    private final ExecContextVariableStateRepository execContextVariableStateRepository;
    private final EventPublisherService eventPublisherService;

    @Transactional
    public void registerVariableStates(Long execContextId, Long execContextVariableStateId, List<VariableUploadedEvent> event) {
        registerVariableStateInternal(execContextId, execContextVariableStateId, event);
    }

    private void registerVariableStateInternal(Long execContextId, Long execContextVariableStateId, List<VariableUploadedEvent> events) {
        register(execContextVariableStateId, (ecpy)-> {
            Set<CheckTaskCanBeFinishedTxEvent> eventsForChecking = new HashSet<>();
            for (VariableUploadedEvent event : events) {
                eventsForChecking.add(new CheckTaskCanBeFinishedTxEvent(execContextId, event.taskId));
                for (ExecContextApiData.VariableState state : ecpy.states) {
                    if (state.taskId.equals(event.taskId)) {
                        if (state.outputs==null || state.outputs.isEmpty()) {
                            log.warn(" (state.outputs==null || state.outputs.isEmpty()) can't process event {}", event);
                        }
                        else {
                            for (ExecContextApiData.VariableInfo output : state.outputs) {
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
            for (CheckTaskCanBeFinishedTxEvent checkTaskCanBeFinishedTxEvent : eventsForChecking) {
                eventPublisherService.publishCheckTaskCanBeFinishedTxEvent(checkTaskCanBeFinishedTxEvent);
            }
        });
    }

    @Transactional
    public void registerCreatedTasks(Long execContextVariableStateId, List<ExecContextApiData.VariableState> events) {
        register(execContextVariableStateId, (ecpy)-> {
            for (ExecContextApiData.VariableState event : events) {
                boolean isNew = true;
                for (ExecContextApiData.VariableState state : ecpy.states) {
                    if (state.taskId.equals(event.taskId)) {
                        isNew = false;
                        if (state.inputs != null && !state.inputs.isEmpty()) {
                            state.inputs.clear();
                        }
                        if (state.outputs != null && !state.outputs.isEmpty()) {
                            state.outputs.clear();
                        }
                        state.inputs = event.inputs;
                        state.outputs = event.outputs;
                        break;
                    }
                }
                if (isNew) {
                    ecpy.states.add(event);
                }
            }
        });
    }

    private Void register(Long execContextVariableStateId, Consumer<ExecContextApiData.ExecContextVariableStates> supplier) {
        ExecContextVariableStateSyncService.checkWriteLockPresent(execContextVariableStateId);

        ExecContextVariableState execContextVariableState = execContextVariableStateCache.findById(execContextVariableStateId);
        if (execContextVariableState==null) {
            log.warn("212.120 ExecContext #{} wasn't found", execContextVariableStateId);
            return null;
        }
        ExecContextApiData.ExecContextVariableStates ecpy = execContextVariableState.getExecContextVariableStateInfo();
        supplier.accept(ecpy);
        execContextVariableState.updateParams(ecpy);

        return null;
    }

    @Transactional
    public void deleteOrphanVariableStates(List<Long> ids) {
        execContextVariableStateRepository.deleteAllByIdIn(ids);
    }

}

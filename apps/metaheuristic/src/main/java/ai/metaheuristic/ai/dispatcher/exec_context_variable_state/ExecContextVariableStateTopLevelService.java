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

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.event.events.TaskCreatedEvent;
import ai.metaheuristic.ai.dispatcher.event.events.VariableUploadedEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Serge
 * Date: 3/20/2021
 * Time: 1:03 AM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecContextVariableStateTopLevelService {

    private final ExecContextVariableStateService execContextVariableStateService;
    private final ExecContextCache execContextCache;

    private static Map<Long, List<ExecContextApiData.VariableState>> taskCreatedEvents = new HashMap<>();
    private static Map<Long, List<VariableUploadedEvent>> variableUploadedEvents = new HashMap<>();

    private final ReentrantReadWriteLock taskLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.WriteLock taskWriteLock = taskLock.writeLock();

    private final ReentrantReadWriteLock varLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.WriteLock varWriteLock = varLock.writeLock();

    public void registerCreatedTask(TaskCreatedEvent event) {
        taskWriteLock.lock();
        try {
            taskCreatedEvents.computeIfAbsent(event.taskVariablesInfo.execContextId, k -> new ArrayList<>()).add(event.taskVariablesInfo);
        } finally {
            taskWriteLock.unlock();
        }
    }

    public void registerVariableState(VariableUploadedEvent event) {
        varWriteLock.lock();
        try {
            variableUploadedEvents.computeIfAbsent(event.execContextId, k -> new ArrayList<>()).add(event);
        } finally {
            varWriteLock.unlock();
        }
    }

    public void processFlushing() {
        processFlushingTaskCreatedEvents();
        processFlushingVariableUploadedEvents();
    }

    private void processFlushingVariableUploadedEvents() {
        Map<Long, List<VariableUploadedEvent>> variableUploadedEventsTemp;
        varWriteLock.lock();
        try {
            if (variableUploadedEvents.isEmpty()) {
                return;
            }
            variableUploadedEventsTemp = variableUploadedEvents;
            variableUploadedEvents = new HashMap<>();
        } finally {
            varWriteLock.unlock();
        }
        processVariableStates(variableUploadedEventsTemp);
        for (Map.Entry<Long, List<VariableUploadedEvent>> entry : variableUploadedEventsTemp.entrySet()) {
            entry.getValue().clear();
        }
        variableUploadedEventsTemp.clear();
    }

    private void processFlushingTaskCreatedEvents() {
        Map<Long, List<ExecContextApiData.VariableState>> taskCreatedEventsTemp;
        taskWriteLock.lock();
        try {
            if (taskCreatedEvents.isEmpty()) {
                return;
            }
            taskCreatedEventsTemp = taskCreatedEvents;
            taskCreatedEvents = new HashMap<>();
        } finally {
            taskWriteLock.unlock();
        }
        processCreatedTasks(taskCreatedEventsTemp);
        for (Map.Entry<Long, List<ExecContextApiData.VariableState>> entry : taskCreatedEventsTemp.entrySet()) {
            entry.getValue().clear();
        }
        taskCreatedEventsTemp.clear();
    }

    private void processCreatedTasks(Map<Long, List<ExecContextApiData.VariableState>> taskCreatedEvents) {
        for (Map.Entry<Long, List<ExecContextApiData.VariableState>> entry : taskCreatedEvents.entrySet()) {
            Long execContextVariableStateId = getExecContextVariableStateId(entry.getKey());
            if (execContextVariableStateId == null) {
                return;
            }
            ExecContextVariableStateSyncService.getWithSyncVoid(execContextVariableStateId,
                    () -> execContextVariableStateService.registerCreatedTasks(execContextVariableStateId, entry.getValue()));
        }
    }

    private void processVariableStates(Map<Long, List<VariableUploadedEvent>> events) {
        for (Map.Entry<Long, List<VariableUploadedEvent>> entry : events.entrySet()) {
            Long execContextVariableStateId = getExecContextVariableStateId(entry.getKey());
            if (execContextVariableStateId == null) {
                return;
            }
            registerVariableStateInternal(entry.getKey(), entry.getValue(), execContextVariableStateId);
        }
    }

    public void registerVariableStateInternal(Long execContextId, List<VariableUploadedEvent> event, Long execContextVariableStateId) {
        ExecContextVariableStateSyncService.getWithSyncVoid(execContextVariableStateId,
                () -> registerVariableStateInternal(execContextId, execContextVariableStateId, event));
    }

    @Nullable
    private Long getExecContextVariableStateId(Long execContextId) {
        ExecContextImpl execContext = execContextCache.findById(execContextId, true);
        if (execContext==null) {
            return null;
        }
        return execContext.execContextVariableStateId;
    }

    // this method is here to work around some strange situation
    // about calling transactional method from lambda
    private void registerVariableStateInternal(Long execContextId, Long execContextVariableStateId, List<VariableUploadedEvent> event) {
        execContextVariableStateService.registerVariableStates(execContextId, execContextVariableStateId, event);
    }


}

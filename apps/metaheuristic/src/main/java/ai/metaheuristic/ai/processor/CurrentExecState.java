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
package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.DispatcherUrl;

@Component
@Profile("processor")
public class CurrentExecState {

    // this is a map for holding the current status of ExecContext, not of task
    private final Map<DispatcherUrl, Map<Long, EnumsApi.ExecContextState>> execContextState = new HashMap<>();
    private final Map<DispatcherUrl, Enums.ExecContextInitState> currentInitStates = new HashMap<>();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    public Enums.ExecContextInitState getCurrentInitState(DispatcherUrl dispatcherUrl) {
        try {
            readLock.lock();
            return currentInitStates.getOrDefault(dispatcherUrl, Enums.ExecContextInitState.NONE);
        } finally {
            readLock.unlock();
        }
    }

    public Map<Long, EnumsApi.ExecContextState> getExecContexts(DispatcherUrl dispatcherUrl) {
        try {
            readLock.lock();
            return new HashMap<>(execContextState.getOrDefault(dispatcherUrl, Map.of()));
        } finally {
            readLock.unlock();
        }
    }

    public Map<EnumsApi.ExecContextState, String> getExecContextsNormalized(DispatcherUrl dispatcherUrl) {
        Map<EnumsApi.ExecContextState, List<Long>> map;
        try {
            readLock.lock();
            Map<Long, EnumsApi.ExecContextState> currStates = execContextState.getOrDefault(dispatcherUrl, Map.of());
            map = new HashMap<>();
            for (Map.Entry<Long, EnumsApi.ExecContextState> entry : currStates.entrySet()) {
                map.computeIfAbsent(entry.getValue(), (k)->new ArrayList<>()).add(entry.getKey());
            }
        } finally {
            readLock.unlock();
        }
        Map<EnumsApi.ExecContextState, String> mapResult = new HashMap<>();
        for (Map.Entry<EnumsApi.ExecContextState, List<Long>> e : map.entrySet()) {
            mapResult.put(e.getKey(), e.getValue().stream().map(Object::toString).collect(Collectors.joining(",")));
        }
        return mapResult;
    }

    public void registerDelta(DispatcherUrl dispatcherUrl, Long execContextId, EnumsApi.ExecContextState state) {
        try {
            writeLock.lock();
            execContextState.computeIfAbsent(dispatcherUrl, m -> new HashMap<>()).put(execContextId, state);
            if (currentInitStates.get(dispatcherUrl)==Enums.ExecContextInitState.NONE) {
                currentInitStates.put(dispatcherUrl, Enums.ExecContextInitState.DELTA);
            }
        } finally {
            writeLock.unlock();
        }
    }

    public void register(DispatcherUrl dispatcherUrl, Map<EnumsApi.ExecContextState, String> states) {
        Map<Long, EnumsApi.ExecContextState> map = new HashMap<>();
        for (Map.Entry<EnumsApi.ExecContextState, String> entry : states.entrySet()) {
            String[] ids = entry.getValue().split(",");
            for (String id : ids) {
                map.put(Long.valueOf(id), entry.getKey());
            }
        }
        try {
            writeLock.lock();

            // there isn't any execContext
            final Map<Long, EnumsApi.ExecContextState> execContextStateMap = execContextState.computeIfAbsent(dispatcherUrl, m -> new HashMap<>());
            if (states.isEmpty()) {
                execContextStateMap.clear();
                return;
            }
            execContextStateMap.putAll(map);
            List<Long> ids = new ArrayList<>();
            execContextStateMap.forEach((key, value) -> {
                boolean isFound = map.entrySet().stream().anyMatch(status -> status.getKey().equals(key));
                if (!isFound) {
                    ids.add(key);
                }
            });
            ids.forEach(execContextStateMap::remove);
            currentInitStates.put(dispatcherUrl, Enums.ExecContextInitState.FULL);

        } finally {
            writeLock.unlock();
        }
    }

    public EnumsApi.ExecContextState getState(DispatcherUrl dispatcherUrl, Long execContextId) {
        try {
            readLock.lock();
            Map<Long, EnumsApi.ExecContextState> map = execContextState.get(dispatcherUrl);
            if (map==null) {
                return EnumsApi.ExecContextState.UNKNOWN;
            }
            Enums.ExecContextInitState state = getCurrentInitState(dispatcherUrl);
            return switch (state) {
                case NONE -> EnumsApi.ExecContextState.UNKNOWN;
                case DELTA -> map.getOrDefault(execContextId, EnumsApi.ExecContextState.UNKNOWN);
                case FULL -> map.getOrDefault(execContextId, EnumsApi.ExecContextState.DOESNT_EXIST);
                default -> throw new IllegalStateException("unknown state: " + state);
            };

        } finally {
            readLock.unlock();
        }
    }

    public boolean notFinishedAndExists(DispatcherUrl dispatcherUrl, Long execContextId) {
        EnumsApi.ExecContextState currState = getState(dispatcherUrl, execContextId);
        return currState!=EnumsApi.ExecContextState.ERROR && currState!= EnumsApi.ExecContextState.FINISHED && currState!= EnumsApi.ExecContextState.DOESNT_EXIST;
    }

    public boolean finishedOrDoesntExist(DispatcherUrl dispatcherUrl, Long execContextId) {
        return !notFinishedAndExists(dispatcherUrl, execContextId);
    }

    public boolean finished(DispatcherUrl dispatcherUrl, Long execContextId) {
        EnumsApi.ExecContextState currState = getState(dispatcherUrl, execContextId);
        return currState==EnumsApi.ExecContextState.ERROR || currState==EnumsApi.ExecContextState.FINISHED;
    }

    boolean isState(DispatcherUrl dispatcherUrl, Long execContextId, EnumsApi.ExecContextState ... state) {
        EnumsApi.ExecContextState currState = getState(dispatcherUrl, execContextId);
        for (EnumsApi.ExecContextState contextState : state) {
            if (currState==contextState) {
                return true;
            }
        }
        return false;
    }

    boolean isStarted(DispatcherUrl dispatcherUrl, Long execContextId) {
        return isState(dispatcherUrl, execContextId, EnumsApi.ExecContextState.STARTED);
    }
}

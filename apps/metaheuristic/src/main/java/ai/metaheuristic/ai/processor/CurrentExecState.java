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
package ai.metaheuristic.ai.processor;

import ai.metaheuristic.api.EnumsApi;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.DispatcherUrl;

@Component
@Profile("processor")
public class CurrentExecState {

    // this is a map for holding the current status of ExecContext, not of task
    private final Map<DispatcherUrl, Map<Long, EnumsApi.ExecContextState>> execContextState = new HashMap<>();
    private final Map<DispatcherUrl, AtomicBoolean> isInit = new HashMap<>();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    public boolean isInited(DispatcherUrl dispatcherUrl) {
        try {
            readLock.lock();
            return isInit.computeIfAbsent(dispatcherUrl, v -> new AtomicBoolean(false)).get();
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

    public void registerDelta(DispatcherUrl dispatcherUrl, Long execContextId, EnumsApi.ExecContextState state) {
        try {
            writeLock.lock();
            execContextState.computeIfAbsent(dispatcherUrl, m -> new HashMap<>()).put(execContextId, state);
            isInit.computeIfAbsent(dispatcherUrl, v -> new AtomicBoolean()).set(true);
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
            isInit.computeIfAbsent(dispatcherUrl, v -> new AtomicBoolean()).set(true);

        } finally {
            writeLock.unlock();
        }
    }

    public EnumsApi.ExecContextState getState(DispatcherUrl host, Long execContextId) {
        try {
            readLock.lock();
            if (!isInited(host)) {
                return EnumsApi.ExecContextState.UNKNOWN;
            }
            if (execContextState.get(host)==null) {
                return EnumsApi.ExecContextState.UNKNOWN;
            }
            return execContextState.get(host).getOrDefault(execContextId, EnumsApi.ExecContextState.DOESNT_EXIST);
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

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

import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.api.EnumsApi;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.*;

@Component
@Profile("processor")
public class CurrentExecState {

    // this is a map for holding the current status of ExecContext, not of task
    private final Map<DispatcherUrl, Map<Long, EnumsApi.ExecContextState>> execContextState = new HashMap<>();

    private final Map<DispatcherUrl, AtomicBoolean> isInit = new HashMap<>();

    public boolean isInited(DispatcherUrl dispatcherUrl) {
        synchronized(execContextState) {
            return isInit.computeIfAbsent(dispatcherUrl, v -> new AtomicBoolean(false)).get();
        }
    }

    public Map<Long, EnumsApi.ExecContextState> getExecContexts(DispatcherUrl dispatcherUrl) {
        synchronized(execContextState) {
            return new HashMap<>(execContextState.getOrDefault(dispatcherUrl, Map.of()));
        }
    }

    public void registerDelta(DispatcherUrl dispatcherUrl, List<KeepAliveResponseParamYaml.ExecContextStatus.SimpleStatus> statuses) {
        synchronized(execContextState) {
            isInit.computeIfAbsent(dispatcherUrl, v -> new AtomicBoolean()).set(true);
            statuses.forEach(status -> execContextState.computeIfAbsent(dispatcherUrl, m -> new HashMap<>()).put(status.id, status.state));
        }
    }

    public void register(DispatcherUrl dispatcherUrl, List<KeepAliveResponseParamYaml.ExecContextStatus.SimpleStatus> statuses) {
        synchronized(execContextState) {
            isInit.computeIfAbsent(dispatcherUrl, v -> new AtomicBoolean()).set(true);
            // there isn't any execContext
            if (statuses.isEmpty()) {
                execContextState.computeIfAbsent(dispatcherUrl, m -> new HashMap<>()).clear();
                return;
            }
            statuses.forEach(status -> execContextState.computeIfAbsent(dispatcherUrl, m -> new HashMap<>()).put(status.id, status.state));
            execContextState.forEach((k, v) -> {
                if (!k.equals(dispatcherUrl)) {
                    return;
                }
                List<Long> ids = new ArrayList<>();
                v.forEach((key, value) -> {
                    boolean isFound = statuses.stream().anyMatch(status -> status.id.equals(key));
                    if (!isFound) {
                        ids.add(key);
                    }
                });
                ids.forEach(v::remove);
            });
        }
    }

    public EnumsApi.ExecContextState getState(DispatcherUrl host, Long execContextId) {
        synchronized(execContextState) {
            if (!isInited(host)) {
                return EnumsApi.ExecContextState.UNKNOWN;
            }
            if (execContextState.get(host)==null) {
                return EnumsApi.ExecContextState.UNKNOWN;
            }
            return execContextState.get(host).getOrDefault(execContextId, EnumsApi.ExecContextState.DOESNT_EXIST);
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

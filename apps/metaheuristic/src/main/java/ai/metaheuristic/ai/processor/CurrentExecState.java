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
package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.api.EnumsApi;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Profile("processor")
public class CurrentExecState {

    // this is a map for holding the current status of ExecContext, not of task
    private final Map<String, Map<Long, EnumsApi.ExecContextState>> execContextState = new HashMap<>();

    private final Map<String, AtomicBoolean> isInit = new HashMap<>();

    public boolean isInited(String dispatcherUrl) {
        synchronized(execContextState) {
            return isInit.computeIfAbsent(dispatcherUrl, v -> new AtomicBoolean(false)).get();
        }
    }

    public void register(String dispatcherUrl, List<KeepAliveResponseParamYaml.ExecContextStatus.SimpleStatus> statuses) {
        synchronized(execContextState) {
            isInit.computeIfAbsent(dispatcherUrl, v -> new AtomicBoolean()).set(true);
            // statuses==null when there isn't any execContext
            if (statuses==null) {
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

    public EnumsApi.ExecContextState getState(String host, Long execContextId) {
        synchronized(execContextState) {
            if (!isInited(host)) {
                return EnumsApi.ExecContextState.UNKNOWN;
            }
            return execContextState.getOrDefault(host, Collections.emptyMap()).getOrDefault(execContextId, EnumsApi.ExecContextState.DOESNT_EXIST);
        }
    }

    boolean isState(String dispatcherUrl, Long execContextId, EnumsApi.ExecContextState state) {
        EnumsApi.ExecContextState currState = getState(dispatcherUrl, execContextId);
        return currState==state;
    }

    boolean isStarted(String dispatcherUrl, Long execContextId) {
        return isState(dispatcherUrl, execContextId, EnumsApi.ExecContextState.STARTED);
    }
}

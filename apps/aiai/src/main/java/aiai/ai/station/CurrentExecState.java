/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package aiai.ai.station;

import aiai.ai.Enums;
import aiai.ai.comm.Protocol;
import aiai.ai.utils.holders.BoolHolder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Profile("station")
public class CurrentExecState {

    // this is a map for holding the current status of FlowInstance, Not a task
    private final Map<String, Map<Long, Enums.FlowInstanceExecState>> flowInstanceState = new HashMap<>();

    private Map<String, BoolHolder> isInit = new HashMap<>();

    public boolean isInited(String launchpadUrl) {
        synchronized(flowInstanceState) {
            return isInit.computeIfAbsent(launchpadUrl, v -> new BoolHolder(false)).value;
        }
    }

    void register(String launchpadUrl, List<Protocol.FlowInstanceStatus.SimpleStatus> statuses) {
        synchronized(flowInstanceState) {
            isInit.computeIfAbsent(launchpadUrl, v -> new BoolHolder()).value = true;
            // statuses==null when there isn't any flow instance
            if (statuses==null) {
                flowInstanceState.computeIfAbsent(launchpadUrl, m -> new HashMap<>()).clear();
                return;
            }
            statuses.forEach(status -> flowInstanceState.computeIfAbsent(launchpadUrl, m -> new HashMap<>()).put(status.flowInstanceId, status.state));
            flowInstanceState.forEach((k, v) -> {
                if (!k.equals(launchpadUrl)) {
                    return;
                }
                List<Long> ids = new ArrayList<>();
                v.forEach((key, value) -> {
                    boolean isFound = statuses.stream().anyMatch(status -> status.flowInstanceId == key);
                    if (!isFound) {
                        ids.add(key);
                    }
                });
                ids.forEach(v::remove);
            });
        }
    }

    Enums.FlowInstanceExecState getState(String host, long flowInstanceId) {
        synchronized(flowInstanceState) {
            if (!isInited(host)) {
                return Enums.FlowInstanceExecState.UNKNOWN;
            }
            return flowInstanceState.getOrDefault(host, Collections.emptyMap()).getOrDefault(flowInstanceId, Enums.FlowInstanceExecState.DOESNT_EXIST);
        }
    }

    boolean isState(String launchpadUrl, long flowInstanceId, Enums.FlowInstanceExecState state) {
        Enums.FlowInstanceExecState currState = getState(launchpadUrl, flowInstanceId);
        return currState!=null && currState==state;
    }

    boolean isStarted(String launchpadUrl, long flowInstanceId) {
        return isState(launchpadUrl, flowInstanceId, Enums.FlowInstanceExecState.STARTED);
    }
}

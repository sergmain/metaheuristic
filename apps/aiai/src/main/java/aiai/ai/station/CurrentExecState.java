/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.station;

import aiai.ai.Enums;
import aiai.ai.comm.Protocol;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CurrentExecState {

    // this is a map for holding the current status of FlowInstance, Not a task
    private final Map<Long, Enums.FlowInstanceExecState> flowInstanceState = new HashMap<>();
    boolean isInit = false;

    void register(List<Protocol.FlowInstanceStatus.SimpleStatus> statuses) {
        synchronized(flowInstanceState) {
            isInit = true;
            // statuses==null when there isn't any flow instance
            if (statuses==null) {
                return;
            }
            for (Protocol.FlowInstanceStatus.SimpleStatus status : statuses) {
                flowInstanceState.put(status.flowInstanceId, status.state);
            }
        }
    }

    void register(long flowInstanceId, Enums.FlowInstanceExecState state) {
        synchronized(flowInstanceState) {
            flowInstanceState.put(flowInstanceId, state);
            isInit = true;
        }
    }


    Enums.FlowInstanceExecState getState(long flowInstanceId) {
        synchronized(flowInstanceState) {
            if (!isInit) {
                return null;
            }
            return flowInstanceState.getOrDefault(flowInstanceId, Enums.FlowInstanceExecState.DOESNT_EXIST);
        }
    }

    boolean isState(long flowInstanceId, Enums.FlowInstanceExecState state) {
        Enums.FlowInstanceExecState currState = getState(flowInstanceId);
        return currState!=null && currState==state;
    }

    boolean isStarted(long flowInstanceId) {
        return isState(flowInstanceId, Enums.FlowInstanceExecState.STARTED);
    }
}

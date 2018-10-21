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

    private final Map<Long, Enums.ExperimentExecState> experimentState = new HashMap<>();
    boolean isInit = false;

    void register(List<Protocol.ExperimentStatus.SimpleStatus> statuses) {
        if (statuses==null) {
            return;
        }
        synchronized(experimentState) {
            for (Protocol.ExperimentStatus.SimpleStatus status : statuses) {
                experimentState.put(status.experimentId, status.state);
            }
            isInit = true;
        }
    }

    Enums.ExperimentExecState getState(long experimentId) {
        synchronized(experimentState) {
            if (!isInit) {
                return null;
            }
            return experimentState.getOrDefault(experimentId, Enums.ExperimentExecState.DOESNT_EXIST);
        }
    }

    boolean isState(long experimentId, Enums.ExperimentExecState state) {
        Enums.ExperimentExecState currState = getState(experimentId);
        return currState!=null && currState==state;
    }

    boolean isStarted(long experimentId) {
        return isState(experimentId, Enums.ExperimentExecState.STARTED);
    }
}

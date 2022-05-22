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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

import java.util.*;

import static ai.metaheuristic.ai.dispatcher.data.ExecContextData.*;

/**
 * @author Serge
 * Date: 10/27/2020
 * Time: 3:51 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextStatusService {

    private final ExecContextRepository execContextRepository;

    private ExecContextStates cachedStatus = null;

    @PostConstruct
    public void post() {
        resetStatus();
    }

    public ExecContextStates getExecContextStatuses() {
        return cachedStatus;
    }

    public KeepAliveResponseParamYaml.ExecContextStatus toExecContextStatus() {
        KeepAliveResponseParamYaml.ExecContextStatus ecs = new KeepAliveResponseParamYaml.ExecContextStatus();
        Map<EnumsApi.ExecContextState, List<String>> map = new HashMap<>();
        for (Map.Entry<Long, EnumsApi.ExecContextState> entry : cachedStatus.statuses.entrySet()) {
            map.computeIfAbsent(entry.getValue(), (o)->new ArrayList<>()).add(entry.getKey().toString());
        }
        for (Map.Entry<EnumsApi.ExecContextState, List<String>> en : map.entrySet()) {
            ecs.statuses.put(en.getKey(), String.join(",", en.getValue()));
        }
        return ecs;
    }

    public boolean isStarted(Long execContextId) {
        for (Map.Entry<Long, EnumsApi.ExecContextState> entry : cachedStatus.statuses.entrySet()) {
            if (entry.getKey().equals(execContextId)) {
                return entry.getValue()==EnumsApi.ExecContextState.STARTED;
            }
        }
        return false;
    }

    public void resetStatus() {
        ExecContextStates cachedStatusTemp = new ExecContextStates();

        for (Object[] allExecState : execContextRepository.findAllExecStates()) {
            cachedStatusTemp.statuses.put((Long) allExecState[0], EnumsApi.ExecContextState.toState((Integer) allExecState[1]));
        }
        cachedStatus = cachedStatusTemp;
    }

    @Nullable
    public EnumsApi.ExecContextState getExecContextState(Long execContextId) {
        return cachedStatus.statuses.get(execContextId);
    }
}

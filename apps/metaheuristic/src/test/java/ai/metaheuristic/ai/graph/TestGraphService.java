/*
 * Metaheuristic, Copyright (C) 2017-2022, Innovation platforms, LLC
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

package ai.metaheuristic.ai.graph;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextGraph;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphCache;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateCache;
import ai.metaheuristic.ai.preparing.PreparingCoreInitService;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Serge
 * Date: 1/6/2022
 * Time: 4:47 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class TestGraphService {

    @Autowired private ExecContextGraphCache execContextGraphCache;
    @Autowired private ExecContextTaskStateCache execContextTaskStateCache;

    public List<ExecContextData.TaskVertex> findLeafs(ExecContextImpl execContext) {
        if (execContext.execContextGraphId==null) {
            return List.of();
        }
        ExecContextGraph ecg = execContextGraphCache.findById(execContext.execContextGraphId);
        if (ecg==null) {
            return List.of();
        }
        return ExecContextGraphService.findLeafs(ecg);
    }

    public Set<ExecContextData.TaskVertex> findDirectAncestors(ExecContextImpl execContext, ExecContextData.TaskVertex vertex) {
        if (execContext.execContextGraphId==null) {
            return Set.of();
        }
        ExecContextGraph ecg = execContextGraphCache.findById(execContext.execContextGraphId);
        if (ecg==null) {
            return Set.of();
        }
        return ExecContextGraphService.findDirectAncestors(ecg, vertex);
    }

    public Set<EnumsApi.TaskExecState> findTaskStates(ExecContextImpl execContext) {
        if (execContext.execContextTaskStateId==null) {
            return Set.of();
        }
        ExecContextTaskState ects = execContextTaskStateCache.findById(execContext.execContextTaskStateId);
        if (ects==null) {
            return Set.of();
        }

        return new HashSet<>(ects.getExecContextTaskStateParamsYaml().states.values());
    }



}

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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * @author Serge
 * Date: 1/24/2020
 * Time: 1:02 AM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextGraphTopLevelService {

    private final ExecContextGraphService execContextGraphService;

    public List<ExecContextData.TaskVertex> findAll(Long execContextGraphId) {
        return execContextGraphService.findAll(execContextGraphId);
    }

    public Set<ExecContextData.TaskVertex> findDescendants(Long execContextGraphId, Long taskId) {
        return execContextGraphService.findDescendants(execContextGraphId, taskId);
    }

    public Set<ExecContextData.TaskVertex> findDirectDescendants(Long execContextGraphId, Long taskId) {
        return execContextGraphService.findDirectDescendants(execContextGraphId, taskId);
    }

    public List<ExecContextData.TaskVertex> findAllForAssigning(Long execContextGraphId, Long execContextTaskStateId) {
        return findAllForAssigning(execContextGraphId, execContextTaskStateId, false);
    }

    public List<ExecContextData.TaskVertex> findAllForAssigning(Long execContextGraphId, Long execContextTaskStateId, boolean includeForCaching) {
        return execContextGraphService.findAllForAssigning(execContextGraphId, execContextTaskStateId, includeForCaching);
    }

}

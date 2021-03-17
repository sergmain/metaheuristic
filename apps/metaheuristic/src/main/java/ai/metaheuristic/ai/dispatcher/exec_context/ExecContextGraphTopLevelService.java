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

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
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

    public List<ExecContextData.TaskVertex> findAll(ExecContextImpl execContext) {
        return execContextGraphService.findAll(execContext);
    }

    public List<ExecContextData.TaskVertex> findLeafs(ExecContextImpl execContext) {
        return execContextGraphService.findLeafs(execContext);
    }

    public Set<ExecContextData.TaskVertex> findDescendants(ExecContextImpl execContext, Long taskId) {
        return execContextGraphService.findDescendants(execContext, taskId);
    }

    public Set<ExecContextData.TaskVertex> findDirectDescendants(ExecContextImpl execContext, Long taskId) {
        return execContextGraphService.findDirectDescendants(execContext, taskId);
    }

    public Set<ExecContextData.TaskVertex> findDirectAncestors(ExecContextImpl execContext, ExecContextData.TaskVertex vertex) {
        return execContextGraphService.findDirectAncestors(execContext, vertex);
    }

    public List<ExecContextData.TaskVertex> findAllForAssigning(ExecContextImpl execContext) {
        return findAllForAssigning(execContext, false);
    }

    public List<ExecContextData.TaskVertex> findAllForAssigning(ExecContextImpl execContext, boolean includeForCaching) {
        return execContextGraphService.findAllForAssigning(execContext, includeForCaching);
    }

}

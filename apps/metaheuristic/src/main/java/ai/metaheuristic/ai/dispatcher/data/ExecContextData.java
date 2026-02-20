/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.data;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextGraph;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.dispatcher.Task;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Serge
 * Date: 2/24/2020
 * Time: 1:48 AM
 */
@Slf4j
public class ExecContextData {

    public record GraphAndStates(ExecContextGraph graph, ExecContextTaskState states) {}

    public record ExecContextDAC(Long execContextId, DirectedAcyclicGraph<TaskVertex, DefaultEdge> graph,
                                 Integer version) {}

    @Data
    @NoArgsConstructor
    public static class ExecContextStates {
        // key - execContextId, value - stae of execContext
        public final Map<Long, EnumsApi.ExecContextState> statuses = new HashMap<>();
    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RootAndParent {
        public Long rootExecContextId;
        public Long parentExecContextId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AssignedTaskComplex {
        public Long execContextId;
        public Task task;
    }

    @Data
    @EqualsAndHashCode(of = "taskId")
    @NoArgsConstructor
    public static class TaskVertex {
        public Long taskId;
        public String taskContextId;

        public TaskVertex(Long taskId) {
            this.taskId = taskId;
        }

        public TaskVertex(Long taskId, String taskContextId) {
            this.taskId = taskId;
            this.taskContextId = taskContextId;
        }
    }

    @Data
    @EqualsAndHashCode(of = "id")
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessVertex {
        public Long id;
        public String process;
        public String processContextId;

        public ProcessVertex(Long id) {
            this.id = id;
        }
    }

    @Data
    @RequiredArgsConstructor
    public static class ReconciliationStatus {
        public final Long execContextId;
        public final AtomicBoolean isNullState = new AtomicBoolean(false);
        public final Set<Long> taskForResettingIds = new HashSet<>();
        public final List<Long> taskIsOkIds = new ArrayList<>();
    }
}

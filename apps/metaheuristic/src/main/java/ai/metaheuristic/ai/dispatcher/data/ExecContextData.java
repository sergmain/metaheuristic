/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.dispatcher.Task;
import lombok.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Serge
 * Date: 2/24/2020
 * Time: 1:48 AM
 */
public class ExecContextData {

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
    @AllArgsConstructor
    public static class TaskVertex {
        public Long taskId;
        public String taskIdStr;
        public EnumsApi.TaskExecState execState =  EnumsApi.TaskExecState.NONE;
        public String taskContextId;

        public TaskVertex(Long taskId) {
            this.taskId = taskId;
            this.taskIdStr = taskId.toString();
        }

        public TaskVertex(Long taskId, Long taskIdLong, EnumsApi.TaskExecState execState, String taskContextId) {
            if (!taskId.equals(taskIdLong)) {
                throw new IllegalStateException("(!taskId.equals(taskIdLong))");
            }
            this.taskId = taskId;
            this.taskIdStr = taskIdLong.toString();
            this.execState = execState;
            this.taskContextId = taskContextId;
        }

        public TaskVertex(Long taskId, String taskContextId) {
            this.taskId = taskId;
            this.taskIdStr = taskId.toString();
            this.taskContextId = taskContextId;
        }

        public TaskVertex(Long taskId, String taskContextId, EnumsApi.TaskExecState execState) {
            this.taskId = taskId;
            this.taskIdStr = taskId.toString();
            this.taskContextId = taskContextId;
            this.execState = execState;
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

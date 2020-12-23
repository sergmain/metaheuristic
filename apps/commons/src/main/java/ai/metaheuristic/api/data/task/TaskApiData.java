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

package ai.metaheuristic.api.data.task;

import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.dispatcher.Task;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Slice;

import java.util.List;

public class TaskApiData {
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class ListOfTasksResult extends BaseDataClass {
        public List<Task> items;
    }

    @Data
    @AllArgsConstructor
    public static class TaskWithContext {
        public Long taskId;
        public String taskContextId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SimpleTaskInfo {
        public Long taskId;
        public String state;
        // taskContextId
        public String context;
        public String process;
        public String functionCode;
    }

    @Data
    @AllArgsConstructor
    public static class TaskState {
        public Long taskId;
        public Integer execState;
        public long updatedOn;

        public TaskState(Object[] o) {
            this.taskId = (Long) o[0];
            this.execState = (Integer) o[1];
            Long longObj = (Long) o[2];
            this.updatedOn = longObj!=null ? longObj : 0;
        }
    }

}

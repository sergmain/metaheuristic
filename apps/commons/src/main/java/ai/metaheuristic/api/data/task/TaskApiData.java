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

package ai.metaheuristic.api.data.task;

import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.dispatcher.Task;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Slice;

import java.util.List;

public class TaskApiData {
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class ListOfTasksResult extends BaseDataClass {
        public List<Task> items;

        @JsonCreator
        public ListOfTasksResult(
            @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
            @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }
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
        public boolean fromCache;
    }

}

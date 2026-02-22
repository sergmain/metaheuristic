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

    public record TaskState(Long taskId, Integer execState, long updatedOn, boolean fromCache, String taskContextId,
                            String processCode) {

/*
        public boolean equals(final Object o) {
            if (o == this) return true;
            if (!(o instanceof TaskState)) return false;
            final TaskState other = (TaskState) o;
            if (!other.canEqual((Object) this)) return false;
            final Object this$taskId = this.taskId();
            final Object other$taskId = other.taskId();
            if (this$taskId == null ? other$taskId != null : !this$taskId.equals(other$taskId)) return false;
            final Object this$execState = this.execState();
            final Object other$execState = other.execState();
            if (this$execState == null ? other$execState != null : !this$execState.equals(other$execState))
                return false;
            if (this.updatedOn() != other.updatedOn()) return false;
            if (this.fromCache() != other.fromCache()) return false;
            return true;
        }

        protected boolean canEqual(final Object other) {
            return other instanceof TaskState;
        }

        public int hashCode() {
            final int PRIME = 59;
            int result = 1;
            final Object $taskId = this.taskId();
            result = result * PRIME + ($taskId == null ? 43 : $taskId.hashCode());
            final Object $execState = this.execState();
            result = result * PRIME + ($execState == null ? 43 : $execState.hashCode());
            final long $updatedOn = this.updatedOn();
            result = result * PRIME + (int) ($updatedOn >>> 32 ^ $updatedOn);
            result = result * PRIME + (this.fromCache() ? 79 : 97);
            return result;
        }
*/

        public String toString() {
            return "TaskApiData.TaskState(taskId=" + this.taskId() + ", execState=" + this.execState() + ", updatedOn=" + this.updatedOn() + ", fromCache=" + this.fromCache() + ", taskContextId=" + this.taskContextId() + ", processCode=" + this.processCode() + ")";
        }
    }

}

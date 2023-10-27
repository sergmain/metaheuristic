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

package ai.metaheuristic.ai.dispatcher.data;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.api.EnumsApi;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Serge
 * Date: 2/24/2020
 * Time: 6:32 PM
 */
public class TaskData {

    public record DetailedTaskContextId(String level, @Nullable String path) {}

/*
    public static class TaskContextId implements CollectionUtils.TreeUtils.TreeItem<String> {
        public String id;
        @Nullable
        public String parentId;
        @Nullable
        public List<TaskContextId> items = null;

        public TaskContextId(String id, String parentId) {
            this.id = id;
            this.parentId = parentId;
        }

        @Nullable
        @Override
        public String getTopId() {
            return parentId;
        }

        @Override
        public String getId() {
            return id;
        }

        @JsonIgnore
        @Nullable
        @Override
        public List<CollectionUtils.TreeUtils.TreeItem<String>> getSubTree() {
            return (List)items;
        }

        @JsonIgnore
        @Override
        public void setSubTree(@Nullable List<CollectionUtils.TreeUtils.TreeItem<String>> list) {
            this.items = (List)list;
        }

        @SuppressWarnings("ConstantValue")
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || this.getClass() != o.getClass()) {
                return false;
            }
            TaskContextId that = (TaskContextId) o;
            if (!this.id.equals(that.id)) {
                return false;
            }
            return true;
        }
    }
*/

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProduceTaskResult {
        public EnumsApi.TaskProducingStatus status;
        public @Nullable String error;
        public Long taskId;

        public ProduceTaskResult(EnumsApi.TaskProducingStatus status, @Nullable String error) {
            this.status = status;
            this.error = error;
        }
    }

    @NoArgsConstructor
    public static class TaskSearching {
        public  Enums.TaskSearchingStatus status = Enums.TaskSearchingStatus.found;
        @Nullable
        public TaskData.AssignedTask task = null;
        public final Map<Long, Enums.TaskRejectingStatus> rejected = new HashMap<>();

        public TaskSearching(@Nullable AssignedTask task) {
            this.task = task;
        }

        public TaskSearching(Enums.TaskSearchingStatus status) {
            this.status = status;
        }
    }

    @RequiredArgsConstructor
    public static class AssignedTask {
        public final TaskImpl task;
        public final String tag;
        public final int quota;
    }

    @RequiredArgsConstructor
    public static class TaskWithRecoveryStatus {
        public final Long taskId;
        public final int triesWasMade;
        public final EnumsApi.TaskExecState targetState;
    }
}

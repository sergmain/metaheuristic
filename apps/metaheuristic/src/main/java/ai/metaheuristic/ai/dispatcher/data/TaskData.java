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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.api.EnumsApi;
import lombok.*;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Serge
 * Date: 2/24/2020
 * Time: 6:32 PM
 */
public class TaskData {

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
        public TaskData.@Nullable AssignedTask task = null;
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

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode(of="taskId")
    public static class TaskWithState {
        public Long taskId;
        public EnumsApi.TaskExecState state;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode(of="taskId")
    public static class TaskWithStateAndTaskContextId {
        public Long taskId;
        public EnumsApi.TaskExecState state;
        public String taskContextId;
    }
}

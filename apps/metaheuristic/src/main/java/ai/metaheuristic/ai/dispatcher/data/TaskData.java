/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

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
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SimpleTaskInfo {
        public Long taskId;
        public String state;
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

        @Nullable public String params;

        public TaskState(Object[] o) {
            this.taskId = (Long) o[0];
            this.execState = (Integer) o[1];
            Long longObj = (Long) o[2];
            this.updatedOn = longObj!=null ? longObj : 0;
            this.params = (String)o[3];
        }
    }
}

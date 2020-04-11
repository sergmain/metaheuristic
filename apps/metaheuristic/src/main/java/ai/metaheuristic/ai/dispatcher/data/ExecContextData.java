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
import ai.metaheuristic.api.dispatcher.Task;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;

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
        // this field is the same as task.params but it could be downgraded to version which is supported by Processor
        public String params;

        public Long execContextId;

        public Task task;
    }


    @Data
    @EqualsAndHashCode(of = "taskId")
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskVertex {
        public @NonNull Long taskId;
        public @NonNull EnumsApi.TaskExecState execState =  EnumsApi.TaskExecState.NONE;
    }

    @Data
    @EqualsAndHashCode(of = "taskId")
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskVertex_140 {
        public Long id;
        public Long taskId;
        public EnumsApi.TaskExecState execState =  EnumsApi.TaskExecState.NONE;

        public TaskVertex_140(Long id) {
            this.id = id;
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
}

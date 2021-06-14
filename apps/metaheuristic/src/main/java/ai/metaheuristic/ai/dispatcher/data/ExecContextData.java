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

package ai.metaheuristic.ai.dispatcher.data;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.dispatcher.Task;
import lombok.*;

import java.io.InputStream;
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
    public static class VariableInitialize {
        public InputStream is;
        public long size;
        public String originFilename;
    }

    @Data
    @NoArgsConstructor
    public static class VariableInitializeList {
        public final List<VariableInitialize> vars = new ArrayList<>();
        public Long execContextId;
        public ExecContextParamsYaml execContextParamsYaml;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SimpleExecContext {
        public Long sourceCodeId;
        public Long execContextId;
        public Long execContextGraphId;
        public Long execContextTaskStateId;
        public Long execContextVariableStateId;
        public Long companyId;
        public ExecContextParamsYaml paramsYaml;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AssignedTaskComplex {
        public Long execContextId;
        public Task task;
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

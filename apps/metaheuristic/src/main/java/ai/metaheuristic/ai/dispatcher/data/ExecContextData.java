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
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Serge
 * Date: 2/24/2020
 * Time: 1:48 AM
 */
@Slf4j
public class ExecContextData {

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
    public static class VariableInitialize {
        public InputStream is;
        public long size;
        public String originFilename;
    }

    @Data
    @NoArgsConstructor
    public static class VariableInitializeList implements Closeable {
        public final List<VariableInitialize> vars = new ArrayList<>();
        public Long execContextId;
        public ExecContextParamsYaml execContextParamsYaml;

        @Override
        public void close() throws IOException {
            for (VariableInitialize var : vars) {
                try {
                    var.is.close();
                }
                catch(Throwable th) {
                    log.error("#439.020 error while closing an input stream", th);
                }
            }
        }
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

/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.sourcing.DiskInfo;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import lombok.*;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * class TaskParamsYaml is for storing parameters of task internally at Processor side
 *
 * class TaskFileParamsYaml is being used for storing a parameters of task for function in a file, ie params-v1.yaml
 *
 * @author Serge
 * Date: 6/17/2019
 * Time: 9:10 PM
 */
@Data
@EqualsAndHashCode
public class TaskParamsYamlV1 implements BaseParams {

    public final int version = 1;

    @Override
    public boolean checkIntegrity() {
        if (S.b(task.processCode)) {
            throw new CheckIntegrityFailedException("processCode is blank");
        }
        if (task.execContextId==null) {
            throw new CheckIntegrityFailedException("execContextId is null");
        }
        if (task.context== EnumsApi.FunctionExecContext.internal && !S.b(task.function.file)) {
            throw new CheckIntegrityFailedException("(task.context== EnumsApi.FunctionExecContext.internal && !S.b(task.function.file))");
        }
        if (task.context== EnumsApi.FunctionExecContext.external && S.b(task.function.file)) {
            throw new CheckIntegrityFailedException("(task.context== EnumsApi.FunctionExecContext.external && S.b(task.function.file))");
        }
        for (OutputVariableV1 output : task.outputs) {
            // global variable as output isn't supported right now
            if (output.context!= EnumsApi.VariableContext.local && output.context!= EnumsApi.VariableContext.array) {
                throw new CheckIntegrityFailedException("(output.context!= EnumsApi.VariableContext.local && output.context!= EnumsApi.VariableContext.array)");
            }
        }
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FunctionInfoV1 {
        public boolean signed;
        /**
         * function's binary length
         */
        public long length;
    }

    /**
     * Resource is related one-to-one to a record in table MH_VARIABLE or in table MH_VARIABLE_GLOBAL
     * for MH_VARIABLE  context will be VariableContext.local
     * for MH_VARIABLE_GLOBAL  context will be VariableContext.global
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ResourceV1 {
        public EnumsApi.VariableContext context;
        public String id;
        public String realName;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InputVariableV1 {
        // it's actually id from a related table - MH_VARIABLE or MH_VARIABLE_GLOBAL
        // for context==VariableContext.local the table is MH_VARIABLE
        // for context==VariableContext.global the table is MH_VARIABLE_GLOBAL
        public @NonNull Long id;
        public @NonNull EnumsApi.VariableContext context;

        public @NonNull String name;
        public @NonNull EnumsApi.DataSourcing sourcing = EnumsApi.DataSourcing.dispatcher;
        public @Nullable GitInfo git;
        public @Nullable DiskInfo disk;

        // name of file if this variable was uploaded from file
        public @Nullable String realName;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OutputVariableV1 {
        // it's actually id from a related table - MH_VARIABLE or MH_VARIABLE_GLOBAL
        // for context==VariableContext.local the table is MH_VARIABLE
        // for context==VariableContext.global the table is MH_VARIABLE_GLOBAL
        public @NonNull Long id;
        public @NonNull EnumsApi.VariableContext context;
        public @NonNull String name;
        public @NonNull EnumsApi.DataSourcing sourcing = EnumsApi.DataSourcing.dispatcher;
        public @Nullable GitInfo git;
        public @Nullable DiskInfo disk;

        // name of file if this variable was uploaded from file
        public @Nullable String realName;

        public boolean uploaded;
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(of = "code")
    public static class FunctionConfigV1 implements Cloneable {

        @SneakyThrows
        public FunctionConfigV1 clone() {
            final FunctionConfigV1 clone = (FunctionConfigV1) super.clone();
            if (this.checksumMap != null) {
                clone.checksumMap = new HashMap<>(this.checksumMap);
            }
            if (this.metas != null) {
                clone.metas = new ArrayList<>(this.metas);
            }
            return clone;
        }

        /**
         * code of function, i.e. simple-app:1.0
         */
        public String code;
        public String type;
        public String file;
        /**
         * params for command line for invoking function
         * <p>
         * this isn't a holder for yaml-based config
         */
        public String params;
        public String env;
        public EnumsApi.FunctionSourcing sourcing;
        public Map<EnumsApi.Type, String> checksumMap;
        public FunctionInfoV1 info = new FunctionInfoV1();
        public String checksum;
        public GitInfo git;
        public boolean skipParams = false;
        public List<Map<String, String>> metas = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class TaskYamlV1 {
        public Long execContextId;
        public @NonNull String taskContextId;
        public String processCode;
        @NonNull public FunctionConfigV1 function;
        @NonNull public final List<FunctionConfigV1> preFunctions = new ArrayList<>();
        @NonNull public final List<FunctionConfigV1> postFunctions = new ArrayList<>();

        public boolean clean = false;
        @NonNull public EnumsApi.FunctionExecContext context;

        public Map<String, Map<String, String>> inline;
        public final List<InputVariableV1> inputs = new ArrayList<>();
        public final List<OutputVariableV1> outputs = new ArrayList<>();
        public final List<Map<String, String>> metas = new ArrayList<>();

        /**
         * Timeout before terminate a process with function
         * value in seconds
         * null or 0 mean the infinite execution
         */
        public Long timeoutBeforeTerminate;

        // fields which are initialized at processor
        public String workingPath;
    }

    public TaskYamlV1 task = new TaskYamlV1();

}

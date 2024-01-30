/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.commons.yaml.task;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.sourcing.DiskInfo;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import lombok.*;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * class TaskParamsYaml is for storing parameters of task in db table MH_TASK
 * AND for storing parameters internally at Processor side
 *
 *
 * class TaskFileParamsYaml is being used for storing a parameters of task for function in a file, ie params-v1.yaml
 *
 * @author Serge
 * Date: 6/17/2019
 * Time: 9:10 PM
 */
@SuppressWarnings("DuplicatedCode")
@Data
@EqualsAndHashCode
public class TaskParamsYamlV2 implements BaseParams {

    @SuppressWarnings("FieldMayBeStatic")
    public final int version = 2;

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
        if (task.context== EnumsApi.FunctionExecContext.external && task.function.sourcing==EnumsApi.FunctionSourcing.dispatcher && S.b(task.function.file)) {
            throw new CheckIntegrityFailedException(
                    "(task.context== EnumsApi.FunctionExecContext.external && " +
                            "task.function.sourcing!= EnumsApi.FunctionSourcing.processor && " +
                            "S.b(task.function.file))");
        }
        for (OutputVariableV2 output : task.outputs) {
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
    public static class InputVariableV2 {
        // it's actually id from a related table - MH_VARIABLE or MH_VARIABLE_GLOBAL
        // for context==VariableContext.local the table is MH_VARIABLE
        // for context==VariableContext.global the table is MH_VARIABLE_GLOBAL
        public Long id;
        public EnumsApi.VariableContext context;

        public String name;
        public EnumsApi.DataSourcing sourcing = EnumsApi.DataSourcing.dispatcher;
        public @Nullable GitInfo git;
        public @Nullable DiskInfo disk;

        // name of file if this variable was uploaded from file
        // for global variable is always null
        // TODO 2020-08-01 real name of file is stored in db, actually.
        //  why is it a null for global variable?
        public @Nullable String filename;

        public @Nullable String type;

        public boolean empty = false;
        private Boolean nullable;

        // This field is used for creating a download link as extension
        @Nullable
        public String ext;

        public Boolean getNullable() {
            return nullable==null ? false : nullable;
        }

        public void setNullable(Boolean nullable) {
            this.nullable = nullable;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OutputVariableV2 {
        // it's actually id from a related table - MH_VARIABLE or MH_VARIABLE_GLOBAL
        // for context==VariableContext.local the table is MH_VARIABLE
        // for context==VariableContext.global the table is MH_VARIABLE_GLOBAL
        public Long id;
        public EnumsApi.VariableContext context;
        public String name;
        public EnumsApi.DataSourcing sourcing = EnumsApi.DataSourcing.dispatcher;
        public @Nullable GitInfo git;
        public @Nullable DiskInfo disk;

        public @Nullable String filename;

        public boolean uploaded;
        public @Nullable String type;

        public boolean empty = false;
        @Nullable
        private Boolean nullable;

        // This field is used for creating a download link as extension
        @Nullable
        public String ext;

        public Boolean getNullable() {
            return nullable != null && nullable;
        }

        public void setNullable(Boolean nullable) {
            this.nullable = nullable;
        }
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(of = "code")
    public static class FunctionConfigV2 implements Cloneable {

        @SneakyThrows
        public FunctionConfigV2 clone() {
            final FunctionConfigV2 clone = (FunctionConfigV2) super.clone();
            if (this.checksumMap != null) {
                clone.checksumMap = new HashMap<>(this.checksumMap);
            }
            clone.metas.addAll(this.metas);
            return clone;
        }

        /**
         * code of function, i.e. simple-app:1.0
         */
        public String code;
        @Nullable
        public String type;

        // Nullable for internal context, NonNull for external
        @Nullable public String file;

        /**
         * params for command line for invoking function
         * <p>
         * this isn't a holder for yaml-based config
         */
        @Nullable
        public String params;

        public String env;
        public EnumsApi.FunctionSourcing sourcing;
        @Nullable public Map<EnumsApi.HashAlgo, String> checksumMap;
        @Nullable public GitInfo git;

        public final List<Map<String, String>> metas = new ArrayList<>();

        public String src = CommonConsts.DEFAULT_FUNCTION_SRC_DIR;
    }

    @Data
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CacheV2 {
        public boolean enabled;
        public boolean omitInline;
        public boolean cacheMeta;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InitV2 {
        public List<Long> parentTaskIds;
        public EnumsApi.TaskExecState nextState;
    }

    @Data
    @NoArgsConstructor
    public static class TaskYamlV2 {
        public Long execContextId;
        public String taskContextId;
        public String processCode;
        public FunctionConfigV2 function;
        public final List<FunctionConfigV2> preFunctions = new ArrayList<>();
        public final List<FunctionConfigV2> postFunctions = new ArrayList<>();

        public boolean clean = false;
        public EnumsApi.FunctionExecContext context;

        @Nullable
        public Map<String, Map<String, String>> inline;

        public final List<InputVariableV2> inputs = new ArrayList<>();
        public final List<OutputVariableV2> outputs = new ArrayList<>();
        public final List<Map<String, String>> metas = new ArrayList<>();

        @Nullable
        public CacheV2 cache;

        // this field has meaning only for state EnumsApi.TaskExecState.INIT
        // must be not null if task.state is EnumsApi.TaskExecState.INIT
        @Nullable
        public InitV2 init;

        /**
         * Timeout before terminate a process with function
         * value in seconds
         * null or 0 mean the infinite execution
         */
        @Nullable
        public Long timeoutBeforeTerminate;

        // fields which are initialized at processor
        public String workingPath;

        @Nullable
        public Integer triesAfterError;

        // all output variables were initialized from cache
        public boolean fromCache;
    }

    public TaskYamlV2 task = new TaskYamlV2();

}

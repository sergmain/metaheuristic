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
@SuppressWarnings("DuplicatedCode")
@Data
@EqualsAndHashCode
public class TaskParamsYaml implements BaseParams {

    public final int version = 1;

    @Override
    public boolean checkIntegrity() {
        if (S.b(task.processCode)) {
            throw new CheckIntegrityFailedException("processCode is blank");
        }
/*
        if (task.function==null) {

        }
*/
        if (task.context== EnumsApi.FunctionExecContext.internal && !S.b(task.function.file)) {
            throw new CheckIntegrityFailedException("(task.context== EnumsApi.FunctionExecContext.internal && !S.b(task.function.file))");
        }
        if (task.context== EnumsApi.FunctionExecContext.external && task.function.sourcing!= EnumsApi.FunctionSourcing.processor && S.b(task.function.file)) {
            throw new CheckIntegrityFailedException(
                    "(task.context== EnumsApi.FunctionExecContext.external && " +
                            "task.function.sourcing!= EnumsApi.FunctionSourcing.processor && " +
                            "S.b(task.function.file))");
        }
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FunctionInfo {
        public boolean signed;
        /**
         * function's binary length
         */
        public long length;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InputVariable {
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
        // for global variable is always null
        // TODO 2020-08-01 real name of file is stored in db, actually. why is it null?
        public @Nullable String realName;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OutputVariable {
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
        // todo 2020-03-12 do we need 'realName' field for OutputVariable?
        public @Nullable String realName;

        public boolean uploaded;
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(of = "code")
    public static class FunctionConfig implements Cloneable {

        @SneakyThrows
        public FunctionConfig clone() {
            final FunctionConfig clone = (FunctionConfig) super.clone();
            if (this.checksumMap != null) {
                clone.checksumMap = new HashMap<>(this.checksumMap);
            }
            clone.metas.addAll(this.metas);
            return clone;
        }

        /**
         * code of function, i.e. simple-app:1.0
         */
        public @NonNull String code;
        public @NonNull String type;

        // Nullable for internal context, NonNull for external
        public @Nullable String file;
        /**
         * params for command line for invoking function
         * <p>
         * this isn't a holder for yaml-based config
         */
        public @NonNull String params;
        public @NonNull String env;
        public @NonNull EnumsApi.FunctionSourcing sourcing;
        public @Nullable Map<EnumsApi.Type, String> checksumMap;
        public @NonNull FunctionInfo info = new FunctionInfo();
        public @Nullable String checksum;
        public @Nullable GitInfo git;
        public boolean skipParams = false;
        public final List<Map<String, String>> metas = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class TaskYaml {
        public @NonNull Long execContextId;
        public @NonNull String taskContextId;
        public @NonNull String processCode;
        public @NonNull FunctionConfig function;
        public @NonNull final List<FunctionConfig> preFunctions = new ArrayList<>();
        public @NonNull final List<FunctionConfig> postFunctions = new ArrayList<>();

        public boolean clean = false;
        public @NonNull EnumsApi.FunctionExecContext context;

        public @Nullable Map<String, Map<String, String>> inline;
        public @NonNull final List<InputVariable> inputs = new ArrayList<>();
        public @NonNull final List<OutputVariable> outputs = new ArrayList<>();
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

    public @NonNull TaskYaml task = new TaskYaml();

}

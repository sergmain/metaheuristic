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
import ai.metaheuristic.api.data.Meta;
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
        for (InputVariableV1 input : task.inputs) {
            if (input.resources.isEmpty()) {
                throw new CheckIntegrityFailedException("(input.resources.isEmpty())");
            }
            if (input.context== EnumsApi.VariableContext.local) {
                if (input.resources.size()>1) {
                    throw new CheckIntegrityFailedException("(input.context== EnumsApi.VariableContext.local)  and (input.resources.size()>1)");
                }
                if (input.resources.get(0).context!= EnumsApi.VariableContext.local) {
                    throw new CheckIntegrityFailedException("(input.resources.get(0).context!= EnumsApi.VariableContext.local)");
                }
            }
            if (input.context== EnumsApi.VariableContext.global) {
                if (input.resources.get(0).context!= EnumsApi.VariableContext.global) {
                    throw new CheckIntegrityFailedException("(input.resources.get(0).context!= EnumsApi.VariableContext.global)");
                }
            }
        }
        if (task.context== EnumsApi.FunctionExecContext.internal && !S.b(task.function.file)) {
            throw new CheckIntegrityFailedException("(task.context== EnumsApi.FunctionExecContext.internal && !S.b(task.function.file))");
        }
        if (task.context== EnumsApi.FunctionExecContext.external && S.b(task.function.file)) {
            throw new CheckIntegrityFailedException("(task.context== EnumsApi.FunctionExecContext.external && S.b(task.function.file))");
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
        public String name;
        public EnumsApi.VariableContext context;
        public EnumsApi.DataSourcing sourcing = EnumsApi.DataSourcing.dispatcher;
        public GitInfo git;
        public DiskInfo disk;

        // Only for case when context==VariableContext.global, the list can contain more than one element
        public final List<ResourceV1> resources = new ArrayList<>();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OutputVariableV1 {
        public @NonNull String name;
        public @NonNull EnumsApi.VariableContext context;
        public @NonNull EnumsApi.DataSourcing sourcing = EnumsApi.DataSourcing.dispatcher;
        public @Nullable GitInfo git;
        public @Nullable DiskInfo disk;
        public @NonNull ResourceV1 resources;
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
                clone.metas = new ArrayList<>();
                for (Meta meta : this.metas) {
                    clone.metas.add(new Meta(meta.key, meta.value, meta.ext));
                }
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
        public List<Meta> metas = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class TaskYamlV1 {
        public Long execContextId;
        public String processCode;
        @NonNull public FunctionConfigV1 function;
        @NonNull public final List<FunctionConfigV1> preFunctions = new ArrayList<>();
        @NonNull public final List<FunctionConfigV1> postFunctions = new ArrayList<>();

        public boolean clean = false;
        @NonNull public EnumsApi.FunctionExecContext context;

        public Map<String, Map<String, String>> inline;
        public final List<InputVariableV1> inputs = new ArrayList<>();
        public final List<OutputVariableV1> outputs = new ArrayList<>();

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

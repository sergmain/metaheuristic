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

package ai.metaheuristic.api.data.exec_context;

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.data.function.SimpleFunctionDefinition;
import ai.metaheuristic.api.sourcing.DiskInfo;
import ai.metaheuristic.api.sourcing.GitInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.tuple.MutablePair;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
public class ExecContextParamsYaml implements BaseParams {

    public final int version = 1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableDeclaration {
        public List<String> globals;
        public String startInputAs;
        public final Map<String, Map<String, String>> inline = new HashMap<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Variable {
        public @NonNull String name;
        public EnumsApi.VariableContext context;
        public EnumsApi.DataSourcing sourcing = EnumsApi.DataSourcing.dispatcher;
        public GitInfo git;
        public DiskInfo disk;
        public Boolean parentContext;

        public Variable(@NonNull String name) {
            this.name = name;
        }

        public Variable(@NonNull EnumsApi.DataSourcing sourcing, @NonNull String name) {
            this.sourcing = sourcing;
            this.name = name;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionDefinition implements SimpleFunctionDefinition {
        public @NonNull String code;
        @Nullable
        public String params;
        public @NonNull EnumsApi.FunctionExecContext context = EnumsApi.FunctionExecContext.external;

        public FunctionDefinition(@NonNull String code) {
            this.code = code;
        }

        public FunctionDefinition(@NonNull String code, @NonNull EnumsApi.FunctionExecContext context) {
            this.code = code;
            this.context = context;
        }
    }

    @Data
    @EqualsAndHashCode(of = {"processCode"})
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Process {

        public @NonNull String processName;
        public @NonNull String processCode;

        public String internalContextId;

        public @NonNull FunctionDefinition function;
        @Nullable
        public List<FunctionDefinition> preFunctions;
        @Nullable
        public List<FunctionDefinition> postFunctions;

        /**
         * Timeout before terminating a process with function
         * value in seconds
         * null or 0 mean the infinite execution
         */
        public Long timeoutBeforeTerminate;
        public @NonNull final List<Variable> inputs = new ArrayList<>();
        public @NonNull final List<Variable> outputs = new ArrayList<>();
        public @NonNull List<MutablePair<String, String>> metas = new ArrayList<>();

        public Process(@NonNull String processName, @NonNull String processCode, @NonNull String internalContextId, @NonNull FunctionDefinition function) {
            this.processName = processName;
            this.processCode = processCode;
            this.internalContextId = internalContextId;
            this.function = function;
        }
    }

    public boolean clean;
    public String sourceCodeUid;
    public final List<Process> processes = new ArrayList<>();
    public final VariableDeclaration variables = new VariableDeclaration();

    // this is a graph of processes for runtime phase
    @NonNull
    public String graph = ConstsApi.EMPTY_GRAPH;

    // this graph is for creating tasks dynamically
    @NonNull
    public String processesGraph = ConstsApi.EMPTY_GRAPH;

    @JsonIgnore
    private HashMap<String, Process> processMap = null;

    @JsonIgnore
    public @Nullable Process findProcess(String processCode) {
        if (processMap==null) {
            processMap = processes.stream().collect(Collectors.toMap(o->o.processCode, o->o, (a, b) -> b, HashMap::new));
        }
        return processMap.get(processCode);
    }

}

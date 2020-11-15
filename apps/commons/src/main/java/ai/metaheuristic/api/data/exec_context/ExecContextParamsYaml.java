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
import lombok.*;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
public class ExecContextParamsYaml implements BaseParams {

    public final int version = 2;

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
        public String name;
        public EnumsApi.VariableContext context;
        public EnumsApi.DataSourcing sourcing = EnumsApi.DataSourcing.dispatcher;
        public GitInfo git;
        public DiskInfo disk;
        public Boolean parentContext;
        @Nullable
        public String type;
        private Boolean nullable;

        public Boolean getNullable() {
            return nullable==null ? false : nullable;
        }

        public void setNullable(Boolean nullable) {
            this.nullable = nullable;
        }

        public Variable(String name) {
            this.name = name;
        }

        public Variable(EnumsApi.DataSourcing sourcing, String name) {
            this.sourcing = sourcing;
            this.name = name;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionDefinition implements SimpleFunctionDefinition {
        public String code;
        @Nullable
        public String params;
        public EnumsApi.FunctionExecContext context = EnumsApi.FunctionExecContext.external;

        public FunctionDefinition(String code) {
            this.code = code;
        }

        public FunctionDefinition(String code, EnumsApi.FunctionExecContext context) {
            this.code = code;
            this.context = context;
        }
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cache {
        public boolean enabled;
    }

    /**
     * !!!!!!!
     * after adding new field,
     * add new mapping in
     * ai.metaheuristic.ai.dispatcher.source_code.graph.SourceCodeGraphLanguageYaml#toProcessForExecCode
     *
     */
    @Data
    @EqualsAndHashCode(of = {"processCode"})
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Process {

        public String processName;
        public String processCode;

        public String internalContextId;

        public FunctionDefinition function;
        @Nullable
        public List<FunctionDefinition> preFunctions;
        @Nullable
        public List<FunctionDefinition> postFunctions;

        @Nullable
        public EnumsApi.SourceCodeSubProcessLogic logic;

        /**
         * Timeout before terminating a process with function
         * value in seconds
         * null or 0 mean the infinite execution
         */
        public Long timeoutBeforeTerminate;
        public final List<Variable> inputs = new ArrayList<>();
        public final List<Variable> outputs = new ArrayList<>();
        public List<Map<String, String>> metas = new ArrayList<>();

        @Nullable
        public Cache cache;

        @Nullable
        public String tags;
        public int priority;

        public Process(String processName, String processCode, String internalContextId, FunctionDefinition function) {
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
    public String graph = ConstsApi.EMPTY_GRAPH;

    // this graph is for creating tasks dynamically
    public String processesGraph = ConstsApi.EMPTY_GRAPH;

    // key - processCode, value - Process
    private HashMap<String, Process> processMap = null;

    // json format, string form of ai.metaheuristic.ai.dispatcher.data.ExecContextData.ExecContextTasksVariablesInfo
    @Nullable
    public String tasksVariablesInfo;

    @JsonIgnore
    @SuppressWarnings("unused")
    private HashMap<String, Process> getProcessMap() {
        return processMap;
    }

    @Nullable
    @JsonIgnore
    public Process findProcess(String processCode) {
        if (processMap==null) {
            processMap = processes.stream().collect(Collectors.toMap(o->o.processCode, o->o, (a, b) -> b, HashMap::new));
        }
        return processMap.get(processCode);
    }

}

/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
import lombok.*;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 01/30/2022
 * Time: 10:15 PM
 */
@Data
public class ExecContextParamsYamlV5 implements BaseParams {

    public final int version = 5;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableDeclarationV5 {
        public List<String> globals;
        public final List<VariableV5> inputs = new ArrayList<>();
        public final List<VariableV5> outputs = new ArrayList<>();
        public final Map<String, Map<String, String>> inline = new HashMap<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableV5 {
        public String name;
        public EnumsApi.VariableContext context;
        public EnumsApi.@Nullable DataSourcing sourcing = EnumsApi.DataSourcing.dispatcher;
        @Nullable
        public GitInfo git;
        @Nullable
        public DiskInfo disk;
        @Nullable
        public Boolean parentContext;
        @Nullable
        public String type;
        @Nullable
        private Boolean nullable;

        // This field is used for creating a download link as extension
        @Nullable
        public String ext;

        public void setSourcing(EnumsApi.DataSourcing sourcing) {
            this.sourcing = sourcing;
        }
        public EnumsApi.DataSourcing getSourcing() {
            return sourcing==null ? EnumsApi.DataSourcing.dispatcher : sourcing;
        }

        @SuppressWarnings("SimplifiableConditionalExpression")
        public Boolean getNullable() {
            return nullable==null ? false : nullable;
        }

        public void setNullable(Boolean nullable) {
            this.nullable = nullable;
        }

        public VariableV5(String name) {
            this.name = name;
        }

        public VariableV5(EnumsApi.DataSourcing sourcing, String name) {
            this.sourcing = sourcing;
            this.name = name;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionDefinitionV5 implements SimpleFunctionDefinition {
        public String code;
        @Nullable
        public String params;
        public EnumsApi.FunctionExecContext context = EnumsApi.FunctionExecContext.external;
        public EnumsApi.FunctionRefType refType = EnumsApi.FunctionRefType.code;

        public FunctionDefinitionV5(String code) {
            this.code = code;
        }

        public FunctionDefinitionV5(String code, EnumsApi.FunctionExecContext context) {
            this.code = code;
            this.context = context;
        }
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheV5 {
        public boolean enabled;
        public boolean omitInline;
        public boolean cacheMeta;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecContextGraphV5 {
        public Long rootExecContextId;
        public Long parentExecContextId;
        public String graph = ConstsApi.EMPTY_GRAPH;
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
    public static class ProcessV5 {

        public String processName;
        public String processCode;

        public String internalContextId;

        public FunctionDefinitionV5 function;
        @Nullable
        public List<FunctionDefinitionV5> preFunctions;
        @Nullable
        public List<FunctionDefinitionV5> postFunctions;

        public EnumsApi.@Nullable SourceCodeSubProcessLogic logic;

        /**
         * Timeout before terminating a process with function
         * value in seconds
         * null or 0 mean the infinite execution
         */
        @Nullable
        public Long timeoutBeforeTerminate;
        public final List<VariableV5> inputs = new ArrayList<>();
        public final List<VariableV5> outputs = new ArrayList<>();
        public List<Map<String, String>> metas = new ArrayList<>();

        @Nullable
        public CacheV5 cache;
        @Nullable
        public String tag;
        public int priority;
        @Nullable
        public String condition;

        @Nullable
        public Integer triesAfterError;

        public ProcessV5(String processName, String processCode, String internalContextId, FunctionDefinitionV5 function) {
            this.processName = processName;
            this.processCode = processCode;
            this.internalContextId = internalContextId;
            this.function = function;
        }
    }

    public boolean clean;
    public String sourceCodeUid;
    public final List<ProcessV5> processes = new ArrayList<>();
    public final VariableDeclarationV5 variables = new VariableDeclarationV5();

    // this graph is for creating tasks dynamically
    public String processesGraph = ConstsApi.EMPTY_GRAPH;

    // Option 5d: dynamic column names for UI state table
    // key = column index, value = display name (function/process name)
    // When populated, this map defines columns instead of processCodes from topology
    public final Map<Integer, String> columnNames = new LinkedHashMap<>();

    @Nullable
    public ExecContextGraphV5 execContextGraph;
}

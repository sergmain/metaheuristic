/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 06/10/2021
 */
@Data
public class ExecContextParamsYamlV3 implements BaseParams {

    public final int version = 3;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableDeclarationV3 {
        public List<String> globals;
        public final List<VariableV3> inputs = new ArrayList<>();
        public final List<VariableV3> outputs = new ArrayList<>();
        public final Map<String, Map<String, String>> inline = new HashMap<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableV3 {
        public String name;
        public EnumsApi.VariableContext context;
        public EnumsApi.DataSourcing sourcing = EnumsApi.DataSourcing.dispatcher;
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

        public VariableV3(String name) {
            this.name = name;
        }

        public VariableV3(EnumsApi.DataSourcing sourcing, String name) {
            this.sourcing = sourcing;
            this.name = name;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionDefinitionV3 implements SimpleFunctionDefinition {
        public String code;
        @Nullable
        public String params;
        public EnumsApi.FunctionExecContext context = EnumsApi.FunctionExecContext.external;

        public FunctionDefinitionV3(String code) {
            this.code = code;
        }

        public FunctionDefinitionV3(String code, EnumsApi.FunctionExecContext context) {
            this.code = code;
            this.context = context;
        }
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheV3 {
        public boolean enabled;
        public boolean omitInline;
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
    public static class ProcessV3 {

        public String processName;
        public String processCode;

        public String internalContextId;

        public FunctionDefinitionV3 function;
        @Nullable
        public List<FunctionDefinitionV3> preFunctions;
        @Nullable
        public List<FunctionDefinitionV3> postFunctions;

        @Nullable
        public EnumsApi.SourceCodeSubProcessLogic logic;

        /**
         * Timeout before terminating a process with function
         * value in seconds
         * null or 0 mean the infinite execution
         */
        @Nullable
        public Long timeoutBeforeTerminate;
        public final List<VariableV3> inputs = new ArrayList<>();
        public final List<VariableV3> outputs = new ArrayList<>();
        public List<Map<String, String>> metas = new ArrayList<>();

        @Nullable
        public CacheV3 cache;
        @Nullable
        public String tags;
        public int priority;
        @Nullable
        public String condition;

        public ProcessV3(String processName, String processCode, String internalContextId, FunctionDefinitionV3 function) {
            this.processName = processName;
            this.processCode = processCode;
            this.internalContextId = internalContextId;
            this.function = function;
        }
    }

    public boolean clean;
    public String sourceCodeUid;
    public final List<ProcessV3> processes = new ArrayList<>();
    public final VariableDeclarationV3 variables = new VariableDeclarationV3();

    // this graph is for creating tasks dynamically
    public String processesGraph = ConstsApi.EMPTY_GRAPH;
}

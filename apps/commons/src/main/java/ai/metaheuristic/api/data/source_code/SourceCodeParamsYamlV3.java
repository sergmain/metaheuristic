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

package ai.metaheuristic.api.data.source_code;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.sourcing.DiskInfo;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import lombok.*;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 06/10/2021
 */
@Data
public class SourceCodeParamsYamlV3 implements BaseParams {

    public final int version=3;

    @Override
    public boolean checkIntegrity() {
        final boolean b = source != null && !S.b(source.uid) && source.processes != null;
        if (!b) {
            throw new CheckIntegrityFailedException("(b = sourceCode != null && !S.b(sourceCode.code) && sourceCode.processes != null) ");
        }
        for (ProcessV3 process : source.processes) {
            if (process.function ==null) {
                throw new CheckIntegrityFailedException("(process.function==null)");
            }
        }
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionDefForSourceCodeV3 {
        public String code;
        public String params;
        public EnumsApi.FunctionExecContext context = EnumsApi.FunctionExecContext.external;

        public FunctionDefForSourceCodeV3(String code) {
            this.code = code;
        }

        public FunctionDefForSourceCodeV3(String code, EnumsApi.FunctionExecContext context) {
            this.code = code;
            this.context = context;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableV3 {
        public String name;
        private EnumsApi.DataSourcing sourcing = EnumsApi.DataSourcing.dispatcher;

        @Nullable
        public GitInfo git;
        @Nullable
        public DiskInfo disk;
        @Nullable
        public Boolean parentContext;
        public boolean array = false;
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

        public VariableV3(String name, EnumsApi.DataSourcing sourcing) {
            this.name = name;
            this.sourcing = sourcing;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubProcessesV3 {
        public EnumsApi.SourceCodeSubProcessLogic logic;
        public List<ProcessV3> processes;
    }

    @Data
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CacheV3 {
        public boolean enabled;
        public boolean omitInline;
    }

    @Data
    @ToString
    public static class ProcessV3 implements Cloneable {

        @SneakyThrows
        public ProcessV3 clone() {
            final ProcessV3 clone = (ProcessV3) super.clone();
            return clone;
        }

        public String name;
        public String code;
        public FunctionDefForSourceCodeV3 function;
        public List<FunctionDefForSourceCodeV3> preFunctions = new ArrayList<>();
        public List<FunctionDefForSourceCodeV3> postFunctions = new ArrayList<>();

        /**
         * Timeout before terminating a process with function
         * value in seconds
         * null or 0 mean the infinite execution
         */
        public Long timeoutBeforeTerminate;
        public final List<VariableV3> inputs = new ArrayList<>();
        public final List<VariableV3> outputs = new ArrayList<>();
        public List<Map<String, String>> metas = new ArrayList<>();
        @Nullable
        public SubProcessesV3 subProcesses;

        @Nullable
        public CacheV3 cache;
        @Nullable
        public String tags;
        public int priority;
        @Nullable
        public String condition;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessControlV3 {
        public String groups;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableDefinitionV3 {
        public List<String> globals;
        public final List<VariableV3> inputs = new ArrayList<>();
        public final List<VariableV3> outputs = new ArrayList<>();
        public final Map<String, Map<String, String>> inline = new HashMap<>();
    }

    @Data
    @ToString
    public static class SourceCodeV3 {
        @Nullable
        public Integer instances;
        public VariableDefinitionV3 variables = new VariableDefinitionV3();
        public List<ProcessV3> processes = new ArrayList<>();
        public boolean clean = false;
        public String uid;
        public List<Map<String, String>> metas = new ArrayList<>();;
        public AccessControlV3 ac;
        @Nullable
        public Boolean strictNaming;
    }

    public SourceCodeV3 source = new SourceCodeV3();

}

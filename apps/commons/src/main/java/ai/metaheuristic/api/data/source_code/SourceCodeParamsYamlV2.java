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

package ai.metaheuristic.api.data.source_code;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.sourcing.DiskInfo;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import lombok.*;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 10/24/2020
 * Time: 11:47 AM
 */
@Data
public class SourceCodeParamsYamlV2 implements BaseParams {

    public final int version=2;

    @Override
    public boolean checkIntegrity() {
        final boolean b = source != null && !S.b(source.uid) && source.processes != null;
        if (!b) {
            throw new CheckIntegrityFailedException("(b = sourceCode != null && !S.b(sourceCode.code) && sourceCode.processes != null) ");
        }
        for (ProcessV2 process : source.processes) {
            if (process.function ==null) {
                throw new CheckIntegrityFailedException("(process.function==null)");
            }
        }
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionDefForSourceCodeV2 {
        public String code;
        public String params;
        public EnumsApi.FunctionExecContext context = EnumsApi.FunctionExecContext.external;

        public FunctionDefForSourceCodeV2(String code) {
            this.code = code;
        }

        public FunctionDefForSourceCodeV2(String code, EnumsApi.FunctionExecContext context) {
            this.code = code;
            this.context = context;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableV2 {
        public EnumsApi.DataSourcing sourcing;
        public GitInfo git;
        public DiskInfo disk;
        public String name;
        public Boolean parentContext;
        public boolean array = false;
        public String type;
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

        public VariableV2(EnumsApi.DataSourcing sourcing, String name) {
            this.sourcing = sourcing;
            this.name = name;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubProcessesV2 {
        public EnumsApi.SourceCodeSubProcessLogic logic;
        public List<ProcessV2> processes;
    }

    @Data
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CacheV2 {
        public boolean enabled;
        public boolean omitInline;
    }

    @Data
    @ToString
    public static class ProcessV2 implements Cloneable {

        @SneakyThrows
        public ProcessV2 clone() {
            final ProcessV2 clone = (ProcessV2) super.clone();
            return clone;
        }

        public String name;
        public String code;
        public FunctionDefForSourceCodeV2 function;
        public List<FunctionDefForSourceCodeV2> preFunctions = new ArrayList<>();
        public List<FunctionDefForSourceCodeV2> postFunctions = new ArrayList<>();

        /**
         * Timeout before terminating a process with function
         * value in seconds
         * null or 0 mean the infinite execution
         */
        public Long timeoutBeforeTerminate;
        public final List<VariableV2> inputs = new ArrayList<>();
        public final List<VariableV2> outputs = new ArrayList<>();
        public List<Map<String, String>> metas = new ArrayList<>();
        @Nullable
        public SubProcessesV2 subProcesses;

        @Nullable
        public CacheV2 cache;
        @Nullable
        public String tags;
        public int priority;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessControlV2 {
        public String groups;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableDefinitionV2 {
        public List<String> globals;
        public String startInputAs;
        public final Map<String, Map<String, String>> inline = new HashMap<>();
    }

    @Data
    @ToString
    public static class SourceCodeV2 {
        @Nullable
        public Integer instances;
        public VariableDefinitionV2 variables = new VariableDefinitionV2();
        public List<ProcessV2> processes = new ArrayList<>();
        public boolean clean = false;
        public String uid;
        public List<Map<String, String>> metas = new ArrayList<>();;
        public AccessControlV2 ac;
    }

    public SourceCodeV2 source = new SourceCodeV2();

}

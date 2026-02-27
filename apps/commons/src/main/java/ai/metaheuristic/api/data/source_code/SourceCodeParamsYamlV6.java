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

package ai.metaheuristic.api.data.source_code;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.sourcing.DiskInfo;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import lombok.*;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 02/25/2026
 * Frozen snapshot of version 6 schema (with Condition class replacing String condition)
 */
@Data
public class SourceCodeParamsYamlV6 implements BaseParams {

    @SuppressWarnings("FieldMayBeStatic")
    public final int version=6;

    @Override
    public boolean checkIntegrity() {
        if (source == null || S.b(source.uid) || source.processes == null) {
            throw new CheckIntegrityFailedException("608.020 (source == null || S.b(source.uid) || source.processes == null)");
        }
        if (source.uid.length()>250) {
            throw new CheckIntegrityFailedException("608.040 uid is too long. max 250 chars");
        }
        for (ProcessV6 process : source.processes) {
            if (process.function ==null) {
                throw new CheckIntegrityFailedException("608.060 (process.function==null)");
            }
        }
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionDefForSourceCodeV6 {
        public String code;
        public String params;
        public EnumsApi.FunctionExecContext context = EnumsApi.FunctionExecContext.external;
        public EnumsApi.FunctionRefType refType = EnumsApi.FunctionRefType.code;

        public FunctionDefForSourceCodeV6(String code) {
            this.code = code;
        }

        public FunctionDefForSourceCodeV6(String code, EnumsApi.FunctionExecContext context) {
            this.code = code;
            this.context = context;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableV6 {
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

        public VariableV6(String name) {
            this.name = name;
        }

        public VariableV6(String name, EnumsApi.DataSourcing sourcing) {
            this.name = name;
            this.sourcing = sourcing;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubProcessesV6 {
        public EnumsApi.SourceCodeSubProcessLogic logic;
        public @Nullable List<ProcessV6> processes;
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheV6 {
        public boolean enabled;
        public boolean omitInline;
        public boolean cacheMeta;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConditionV6 {
        public String conditions;
        public EnumsApi.SkipPolicy skipPolicy = EnumsApi.SkipPolicy.normal;

        public ConditionV6(String conditions) {
            this.conditions = conditions;
        }
    }

    @Data
    @ToString
    public static class ProcessV6 implements Cloneable {

        @SneakyThrows
        public ProcessV6 clone() {
            final ProcessV6 clone = (ProcessV6) super.clone();
            return clone;
        }

        public String name;
        public String code;
        public FunctionDefForSourceCodeV6 function;
        @Nullable
        public List<FunctionDefForSourceCodeV6> preFunctions = new ArrayList<>();
        @Nullable
        public List<FunctionDefForSourceCodeV6> postFunctions = new ArrayList<>();

        @Nullable
        public Long timeoutBeforeTerminate;
        public final List<VariableV6> inputs = new ArrayList<>();
        public final List<VariableV6> outputs = new ArrayList<>();
        public List<Map<String, String>> metas = new ArrayList<>();
        @Nullable
        public SubProcessesV6 subProcesses;

        @Nullable
        public CacheV6 cache;

        @Nullable
        public String tag;
        public int priority;
        @Nullable
        public ConditionV6 condition;
        @Nullable
        public Integer triesAfterError;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessControlV6 {
        public String groups;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableDefinitionV6 {
        public List<String> globals;
        public final List<VariableV6> inputs = new ArrayList<>();
        public final List<VariableV6> outputs = new ArrayList<>();
        public final Map<String, Map<String, String>> inline = new HashMap<>();
    }

    @Data
    @ToString
    public static class SourceCodeV6 {
        @Nullable
        public Integer instances;
        @Nullable
        public VariableDefinitionV6 variables = new VariableDefinitionV6();
        public @Nullable List<ProcessV6> processes = new ArrayList<>();
        public boolean clean = false;
        public String uid;
        @Nullable
        public List<Map<String, String>> metas = new ArrayList<>();
        public @Nullable AccessControlV6 ac;
        @Nullable
        public Boolean strictNaming;
    }

    public SourceCodeV6 source = new SourceCodeV6();
}

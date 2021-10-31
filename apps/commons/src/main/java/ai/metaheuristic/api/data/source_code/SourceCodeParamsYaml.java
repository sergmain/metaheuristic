/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.function.SimpleFunctionDefinition;
import ai.metaheuristic.api.sourcing.DiskInfo;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import ai.metaheuristic.commons.utils.MetaUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 9:01 PM
 */
@Data
public class SourceCodeParamsYaml implements BaseParams {

    public final int version=3;

    @Override
    public boolean checkIntegrity() {
        if (source == null || S.b(source.uid) || source.processes == null) {
            throw new CheckIntegrityFailedException("#608.020 (source == null || S.b(source.uid) || source.processes == null)");
        }
        if (source.uid.length()>50) {
            throw new CheckIntegrityFailedException("#608.040 uid is too long. max 50 chars");
        }
        for (Process process : source.processes) {
            if (process.function ==null) {
                throw new CheckIntegrityFailedException("#608.060 (process.function==null)");
            }
            if (StringUtils.containsAny(process.tags, ',', ' ')) {
                throw new CheckIntegrityFailedException("#608.080 process.tags can't contain comma or space and must be handled as single tag");
            }
        }
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionDefForSourceCode implements SimpleFunctionDefinition {
        public String code;
        public String params;
        public EnumsApi.FunctionExecContext context = EnumsApi.FunctionExecContext.external;

        public FunctionDefForSourceCode(String code) {
            this.code = code;
        }

        public FunctionDefForSourceCode(String code, EnumsApi.FunctionExecContext context) {
            this.code = code;
            this.context = context;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Variable {
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

        public Variable(String name) {
            this.name = name;
        }

        public Variable(String name, EnumsApi.DataSourcing sourcing) {
            this.name = name;
            this.sourcing = sourcing;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubProcesses {
        public EnumsApi.SourceCodeSubProcessLogic logic;
        public List<Process> processes;
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cache {
        public boolean enabled;
        public boolean omitInline;
    }

    @Data
    @ToString
    public static class Process implements Cloneable {

        @SneakyThrows
        public Process clone() {
            final Process clone = (Process) super.clone();
            return clone;
        }

        public String name;
        public String code;
        public FunctionDefForSourceCode function;
        public @Nullable List<FunctionDefForSourceCode> preFunctions = new ArrayList<>();
        public @Nullable List<FunctionDefForSourceCode> postFunctions = new ArrayList<>();

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
        public SubProcesses subProcesses;

        @JsonIgnore
        @Nullable public Meta getMeta(String key) {
            return MetaUtils.getMeta(metas, key);
        }

        @Nullable
        public Cache cache;

        // TODO 2021-10-24 next time when version of SourceCodeParamsYaml will be increased, rename this field to 'tag'
        @Nullable
        public String tags;
        public int priority;
        @Nullable
        public String condition;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessControl {
        public String groups;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableDefinition {
        public List<String> globals;
        public final List<Variable> inputs = new ArrayList<>();
        public final List<Variable> outputs = new ArrayList<>();
        public final Map<String, Map<String, String>> inline = new HashMap<>();
    }

    @Data
    @ToString
    public static class SourceCodeYaml {
        @Nullable
        public Integer instances;
        public VariableDefinition variables = new VariableDefinition();
        public List<Process> processes = new ArrayList<>();
        public boolean clean = false;
        public String uid;
        public List<Map<String, String>> metas = new ArrayList<>();;
        public AccessControl ac;
        @Nullable
        public Boolean strictNaming;

        @JsonIgnore
        @Nullable
        public Meta getMeta(String key) {
            return MetaUtils.getMeta(metas, key);
        }
    }

    public SourceCodeYaml source = new SourceCodeYaml();
}

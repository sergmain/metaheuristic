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

package ai.metaheuristic.commons.yaml.task;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.sourcing.DiskInfo;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import lombok.*;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Frozen snapshot of the V3 schema (per-OS FunctionConfig 'targets'). Mirrors the
 * version-less TaskParamsYaml at the time V3 became latest. Never modify its field set.
 *
 * class TaskParamsYamlV3 is for storing parameters of task in db table MH_TASK
 * AND for storing parameters internally at Processor side.
 *
 * <br/>
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
public class TaskParamsYamlV3 implements BaseParams {

    public final int version = 3;

    @Override
    public boolean checkIntegrity() {
        if (S.b(task.processCode)) {
            throw new CheckIntegrityFailedException("processCode is blank");
        }
        if (task.execContextId==null) {
            throw new CheckIntegrityFailedException("execContextId is null");
        }
        if (task.context== EnumsApi.FunctionExecContext.internal && !task.function.targets.isEmpty()) {
            throw new CheckIntegrityFailedException("(task.context== EnumsApi.FunctionExecContext.internal && !task.function.targets.isEmpty())");
        }
        if (task.context== EnumsApi.FunctionExecContext.external && task.function.sourcing==EnumsApi.FunctionSourcing.dispatcher && task.function.targets.isEmpty()) {
            throw new CheckIntegrityFailedException(
                    "(task.context== EnumsApi.FunctionExecContext.external && " +
                            "task.function.sourcing==EnumsApi.FunctionSourcing.dispatcher && " +
                            "task.function.targets.isEmpty())");
        }
        for (OutputVariableV3 output : task.outputs) {
            // global variable as output isn't supported right now
            if (output.context!= EnumsApi.VariableContext.local && output.context!= EnumsApi.VariableContext.array) {
                throw new CheckIntegrityFailedException("(output.context!= EnumsApi.VariableContext.local && output.context!= EnumsApi.VariableContext.array)");
            }
        }
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InputVariableV3 {
        // it's actually id from a related table - MH_VARIABLE or MH_VARIABLE_GLOBAL
        // for context==VariableContext.local the table is MH_VARIABLE
        // for context==VariableContext.global the table is MH_VARIABLE_GLOBAL
        public Long id;
        public EnumsApi.VariableContext context;

        public String name;
        public EnumsApi.DataSourcing sourcing = EnumsApi.DataSourcing.dispatcher;
        public @Nullable GitInfo git;
        public @Nullable DiskInfo disk;

        // name of file if this variable was uploaded from file
        // for global variable is always null
        // TODO 2020-08-01 real name of file is stored in db, actually.
        //  why is it a null for global variable?
        public @Nullable String filename;

        public @Nullable String type;

        // true if variable is null or length==0
        public boolean empty = false;

        // could variable be null
        private Boolean nullable;

        // This field is used for creating a download link as extension
        @Nullable
        public String ext;

        public Boolean getNullable() {
            return nullable != null && nullable;
        }

        public void setNullable(Boolean nullable) {
            this.nullable = nullable;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OutputVariableV3 {
        // it's actually id from a related table - MH_VARIABLE or MH_VARIABLE_GLOBAL
        // for context==VariableContext.local the table is MH_VARIABLE
        // for context==VariableContext.global the table is MH_VARIABLE_GLOBAL
        public Long id;
        public EnumsApi.VariableContext context;
        public String name;
        public EnumsApi.DataSourcing sourcing = EnumsApi.DataSourcing.dispatcher;
        public @Nullable GitInfo git;
        public @Nullable DiskInfo disk;

        public @Nullable String filename;

        public boolean uploaded;
        public @Nullable String type;

        public boolean empty = false;
        @Nullable
        private Boolean nullable;

        // This field is used as extension for creating a download link
        @Nullable
        public String ext;

        public Boolean getNullable() {
            return nullable != null && nullable;
        }

        public void setNullable(Boolean nullable) {
            this.nullable = nullable;
        }
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(of = "code")
    public static class FunctionConfigV3 implements Cloneable {

        @SneakyThrows
        public FunctionConfigV3 clone() {
            final FunctionConfigV3 clone = (FunctionConfigV3) super.clone();
            if (this.checksumMap != null) {
                clone.checksumMap = new HashMap<>(this.checksumMap);
            }
            clone.metas.addAll(this.metas);
            clone.targets = new LinkedHashMap<>();
            for (Map.Entry<String, TargetV3> e : this.targets.entrySet()) {
                clone.targets.put(e.getKey(), e.getValue().clone());
            }
            return clone;
        }

        /**
         * code of function, i.e. simple-app:1.0
         */
        public String code;
        @Nullable
        public String type;

        // Nullable for internal context, NonNull for external
        /**
         * per-OS deployment targets: key is an OsArch key (e.g. linux_amd64) or
         * {@link CommonConsts#MH_DEFAULT_OS_KEY}. Replaces the former single src+file pair.
         */
        public Map<String, TargetV3> targets = new LinkedHashMap<>();

        /**
         * params for command line for invoking function
         * <p>
         * this isn't a holder for yaml-based config
         */
        @Nullable
        public String params;

        public @Nullable String env;
        public EnumsApi.FunctionSourcing sourcing;
        @Nullable
        public Map<EnumsApi.HashAlgo, String> checksumMap;
        @Nullable
        public GitInfo git;

        public final List<Map<String, String>> metas = new ArrayList<>();

        @Nullable
        public String assetDir;

        // Stage 5 (vault secret handoff): @Nullable per the @Nullable-exception
        // rule — no version bump. When non-null and task.companyId != 0L, the
        // Processor fetches a sealed API key from the Dispatcher before launching
        // the Function.
        @Nullable
        public ApiV3 api;
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TargetV3 implements Cloneable {
        public String src = CommonConsts.DEFAULT_FUNCTION_SRC_DIR;
        public @Nullable String file;

        @SneakyThrows
        public TargetV3 clone() {
            return (TargetV3) super.clone();
        }
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiV3 {
        public String keyCode;
    }

    @Data
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CacheV3 {
        public boolean enabled;
        public boolean omitInline;
        public boolean cacheMeta;
    }

    @Data
    @NoArgsConstructor
    public static class InitV3 {
        public List<Long> parentTaskIds;
        public EnumsApi.TaskExecState nextState;

        public InitV3(List<Long> parentTaskIds, EnumsApi.TaskExecState nextState) {
            this.parentTaskIds = parentTaskIds;
            this.nextState = nextState;
        }
    }

    @Data
    @NoArgsConstructor
    public static class TaskYamlV3 {
        public Long execContextId;
        public String taskContextId;
        public String processCode;
        public FunctionConfigV3 function;
        public final List<FunctionConfigV3> preFunctions = new ArrayList<>();
        public final List<FunctionConfigV3> postFunctions = new ArrayList<>();

        public boolean clean = false;
        public EnumsApi.FunctionExecContext context;

        @Nullable public Map<String, Map<String, String>> inline;
        public final List<InputVariableV3> inputs = new ArrayList<>();
        public final List<OutputVariableV3> outputs = new ArrayList<>();
        public final List<Map<String, String>> metas = new ArrayList<>();

        @Nullable public CacheV3 cache;

        // this field has meaning only for state EnumsApi.TaskExecState.INIT
        // must be not null if task.state is EnumsApi.TaskExecState.INIT
        @Nullable
        public InitV3 init;

        /**
         * Timeout before terminate a process with function
         * value in seconds
         * null or 0 mean the infinite execution
         */
        @Nullable
        public Long timeoutBeforeTerminate;

        // fields which are initialized at processor
        public String workingPath;
        @Nullable
        public Integer triesAfterError;

        // all output variables were initialized from cache
        public boolean fromCache;
    }

    public TaskYamlV3 task = new TaskYamlV3();

    // Stage 5: the company that owns this task's ExecContext. Used by the
    // Processor at launch time to resolve API keys via the Vault. Greenfield
    // exception per project agreement: primitive long added directly to both
    // the version-less class and the latest versioned class without bumping
    // the version. A zero value means "no company" — Processor must treat as
    // "no API keys available" and skip the sealed-secret fetch.
    public long companyId;

}

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
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
public class ExecContextParamsYaml implements BaseParams {

    @SuppressWarnings("FieldMayBeStatic")
    public final int version = 6;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableDeclaration {
        @Nullable
        public List<String> globals;
        public final List<Variable> inputs = new ArrayList<>();
        public final List<Variable> outputs = new ArrayList<>();
        public final Map<String, Map<String, String>> inline = new HashMap<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Variable {
        public String name;
        public EnumsApi.VariableContext context;
        public EnumsApi.DataSourcing sourcing = EnumsApi.DataSourcing.dispatcher;
        @Nullable
        public GitParams git;
        @Nullable
        public DiskParams disk;
        @Nullable
        public Boolean parentContext;
        @Nullable
        public String type;
        // if true, this variable can be null
        @Nullable
        private Boolean nullable;

        // This field is used for creating a download link as extension
        @Nullable
        public String ext;

        // if true, this variable can be reassigned in sub-contexts
        @Deprecated(forRemoval = true)
        @Nullable
        public Boolean mutable;

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
        public EnumsApi.FunctionRefType refType = EnumsApi.FunctionRefType.code;

        public FunctionDefinition(String code) {
            this.code = code;
        }

        public FunctionDefinition(String code, EnumsApi.FunctionExecContext context) {
            this.code = code;
            this.context = context;
        }
    }

    /**
     * A git revision pinned for ONE external, git-sourced Function, resolved once when the ExecContext
     * is created and constant for the whole life of that ExecContext.
     *
     * <p>The Function descriptor may name a moving target - branch tip, i.e. commit=HEAD. That is a
     * POLICY ("use the latest"), not a revision, and resolving it independently on each Processor at
     * each Task would let two Tasks of one ExecContext run different code. So the branch tip is resolved
     * to a concrete sha here, at creation time, and every Task produced from this ExecContext carries
     * that sha.
     *
     * <p>git.commit always holds the RESOLVED sha, never HEAD - that is the whole point of this class.
     */
    /**
     * This class's own git shape - a deliberate copy of {@link GitInfo} rather than a reuse of it.
     *
     * <p>GitInfo is shared across unrelated owners: Variables, DataStorage and the Function descriptor.
     * Everything an ExecContext holds - a git-sourced Variable and the pinned revision of a git-sourced
     * Function alike - uses GitParams instead, so this params class owns its own type and can evolve
     * without dragging the other owners of GitInfo along.
     *
     * <p>Fields mirror GitInfo exactly, INCLUDING their names, so the stored yaml is unchanged and no
     * migration is needed. Conversion to and from GitInfo happens only at the boundary, below.
     *
     * <p>On a {@link GitSourceInfo}, `commit` holds the RESOLVED sha, never HEAD.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GitParams {
        public String repo;
        // right now it'll be always as origin
//        public String remote;
        public String branch;
        public String commit;
        public String path;

        @Nullable
        public static GitParams from(@Nullable GitInfo git) {
            return git==null ? null : new GitParams(git.repo, git.branch, git.commit, git.path);
        }

        @Nullable
        public static GitInfo toGitInfo(@Nullable GitParams git) {
            return git==null ? null : new GitInfo(git.repo, git.branch, git.commit, git.path);
        }
    }

    /**
     * This class's own disk shape - a deliberate copy of {@link DiskInfo}, for the same reason
     * {@link GitParams} copies GitInfo: DiskInfo is shared with DataStorage, SourceCodeParamsYaml and
     * TaskParamsYaml, and an ExecContext's params should own every type it stores.
     *
     * <p>Fields mirror DiskInfo exactly, INCLUDING their names, so the stored yaml is unchanged and no
     * migration is needed. Conversion happens only at the boundary, below.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiskParams {
        /**
         * A file mask. Can include * and ? as well
         */
        public String mask;

        /**
         * A code for directory. This code must be configured at processor side in file env.yaml
         */
        public String code;

        /**
         * A direct path to file(s), path + mask
         * i.e. \tmp\some-dir\file??.*
         */
        public String path;

        @Nullable
        public static DiskParams from(@Nullable DiskInfo disk) {
            return disk==null ? null : new DiskParams(disk.mask, disk.code, disk.path);
        }

        @Nullable
        public static DiskInfo toDiskInfo(@Nullable DiskParams disk) {
            return disk==null ? null : new DiskInfo(disk.mask, disk.code, disk.path);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GitSourceInfo {
        public String functionCode;
        public GitParams git;
    }

    /**
     * All git revisions this ExecContext is pinned to, one entry per git-sourced external Function
     * found in the DAG. Null when the DAG uses no git-sourced Function at all, which is the common case.
     */
    @Data
    @NoArgsConstructor
    public static class GitSources {
        public final List<GitSourceInfo> gitSourceInfos = new ArrayList<>();

        @JsonIgnore
        @Nullable
        public GitSourceInfo find(String functionCode) {
            for (GitSourceInfo info : gitSourceInfos) {
                if (info.functionCode.equals(functionCode)) {
                    return info;
                }
            }
            return null;
        }
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cache {
        public boolean enabled;
        public boolean omitInline;
        public boolean cacheMeta;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecContextGraph {
        public Long rootExecContextId;
        public Long parentExecContextId;
        public String graph = ConstsApi.EMPTY_GRAPH;

        public ExecContextGraph(Long rootExecContextId, Long parentExecContextId) {
            this.rootExecContextId = rootExecContextId;
            this.parentExecContextId = parentExecContextId;
        }
    }

    /**
     * !!!!!!!
     * after adding new field,
     * add a new mapping in
     * @see ai.metaheuristic.ai.dispatcher.source_code.graph.SourceCodeGraphLanguageYaml#toProcessForExecCode
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

        public EnumsApi.@Nullable SourceCodeSubProcessLogic logic;

        /**
         * Timeout before terminating a process with function
         * value in seconds
         * null or 0 mean the infinite execution
         */
        @Nullable
        public Long timeoutBeforeTerminate;
        public final List<Variable> inputs = new ArrayList<>();
        public final List<Variable> outputs = new ArrayList<>();
        public List<Map<String, String>> metas = new ArrayList<>();

        @Nullable
        public Cache cache;

        @Nullable
        public String tag;
        public int priority;
        @Nullable
        public String condition;

        @Nullable
        public Integer triesAfterError;

        // DSL v2 - if non-null, this process is an in-band GRAFT node (a native group-call): the dispatcher
        // expands the named group here via attachGroup instead of producing a task. NOT an internal function.
        @Nullable
        public Graft graft;

        public Process(String processName, String processCode, String internalContextId, FunctionDefinition function) {
            this.processName = processName;
            this.processCode = processCode;
            this.internalContextId = internalContextId;
            this.function = function;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Group {
        public String name;
        public final List<Process> body = new ArrayList<>();
        public final List<Variable> inputs = new ArrayList<>();
        public final List<Variable> outputs = new ArrayList<>();
        @Nullable
        public String internalContextId;
        @Nullable
        public String resetPointProcessCode;

        public Group(String name) {
            this.name = name;
        }
    }

    // DSL v2 - an in-band graft instruction (a native group-call node): instantiate group `groupName`
    // at this point in the flow. bindings map outer variable names to the group's declared I/O (positional).
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Graft {
        public String groupName;
        public final List<String> inputBindings = new ArrayList<>();
        public final List<String> outputBindings = new ArrayList<>();
        @Nullable
        public String driver;
        @Nullable
        public String at;

        public Graft(String groupName) {
            this.groupName = groupName;
        }
    }

    public boolean clean;
    public String sourceCodeUid;

    @Nullable
    public String desc;

    public final List<Process> processes = new ArrayList<>();
    public final List<Group> groups = new ArrayList<>();
    public final VariableDeclaration variables = new VariableDeclaration();

    // this graph is for creating tasks dynamically
    public String processesGraph = ConstsApi.EMPTY_GRAPH;

    // Option 5d: dynamic column names for UI state table
    // key = column index, value = display name (function/process name)
    // When populated, this map defines columns instead of processCodes from topology
    public final Map<Integer, String> columnNames = new LinkedHashMap<>();

    @Nullable
    public ExecContextGraph execContextGraph;

    // git revisions this ExecContext is pinned to; null when no git-sourced Function is in the DAG
    @Nullable
    public GitSources gitSources;

    @JsonIgnore
    private @Nullable HashMap<String, Process> getProcessMap() {
        return processMap;
    }

    // key - processCode, value - Process
    private @Nullable HashMap<String, Process> processMap = null;

    @Nullable
    @JsonIgnore
    public Process findProcess(String processCode) {
        if (processMap==null) {
            processMap = processes.stream().collect(Collectors.toMap(o->o.processCode, o->o, (a, b) -> b, HashMap::new));
            // DSL v2: a grafted group body process runs as a real task but lives in groups[].body, not in the
            // main processes list; index it so the grafted task resolves its process at run time (main wins).
            for (Group g : groups) {
                for (Process p : g.body) {
                    processMap.putIfAbsent(p.processCode, p);
                }
            }
        }
        return processMap.get(processCode);
    }

}

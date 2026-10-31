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

package ai.metaheuristic.ai.dispatcher.data;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.dispatcher.Task;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Serge
 * Date: 2/24/2020
 * Time: 1:48 AM
 */
@Slf4j
public class ExecContextData {

    public record ExecContextCreationInfo(String desc){}

    /**
     * The value supplied for one source-level input variable when an ExecContext is created. A holder rather than
     * a bare String so that "no value" can be said explicitly: {@code value == null} initializes the variable
     * NULLIFIED (initialized, no content - the same state MH gives a variable set as null), while an absent map
     * entry still means "not supplied" and is refused. A null is accepted only for an input the SourceCode declares
     * nullable ('?'); for any other it is refused (01.562.124).
     */
    public record VariableValue(@Nullable String value){}

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecContextDescItem {
        public Long id;
        @Nullable
        public String desc;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecContextDescsResult {
        public List<ExecContextDescItem> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecContextDescsRequest {
        public @Nullable List<Long> ids;
    }

    /**
     * Body of {@code POST /rest/v1/dispatcher/source-code/exec-context-with-variables-add-commit}: the SourceCode to
     * instantiate, and the values of its source-level input variables as name -&gt; value. A JSON null value becomes
     * {@code VariableValue(null)} - the input is created NULLIFIED, which is accepted only for an input the SourceCode
     * declares nullable ('?'). A non-string JSON scalar arrives as its text. Every declared input must be named, and
     * no undeclared name may be (562.120 / 562.122, enforced where the variables are created).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecContextWithVariablesRequest {
        public @Nullable Long sourceCodeId;
        public @Nullable Map<String, @Nullable String> variables;
    }

    /**
     * Answer of the same endpoint. {@code execContextId} is set when the ExecContext was created and its Tasks were
     * produced; otherwise it is null and {@code errorMessages} says why - creation is all-or-nothing, so nothing is left
     * behind.
     */
    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class ExecContextWithVariablesResult extends BaseDataClass {
        public @Nullable Long execContextId;
        public @Nullable Long sourceCodeId;
        public @Nullable String sourceCodeUid;
        public @Nullable Integer state;
        public @Nullable String stateName;

        /**
         * Names errorMessages / infoMessages explicitly. Without it neither is serialized: BaseDataClass's
         * {@code @JsonIgnore isErrorMessages()} is an accessor of the same logical property, and an ignoral on one
         * accessor drops the whole property unless another names it with {@code @JsonProperty} - observed in
         * {@code ExecContextWithVariablesRestControllerTest}: an answer without its refusal.
         */
        @JsonCreator
        public ExecContextWithVariablesResult(
                @JsonProperty("execContextId") @Nullable Long execContextId,
                @JsonProperty("sourceCodeId") @Nullable Long sourceCodeId,
                @JsonProperty("sourceCodeUid") @Nullable String sourceCodeUid,
                @JsonProperty("state") @Nullable Integer state,
                @JsonProperty("stateName") @Nullable String stateName,
                @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
                @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.execContextId = execContextId;
            this.sourceCodeId = sourceCodeId;
            this.sourceCodeUid = sourceCodeUid;
            this.state = state;
            this.stateName = stateName;
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }
    }



    // 041 Phase 21: GraphAndStates (the whole-ExecContext graph and task-state records) is gone with the records

    public record ExecContextDAC(Long execContextId, DirectedAcyclicGraph<TaskVertex, DefaultEdge> graph,
                                 Integer version) {}

    @Data
    @NoArgsConstructor
    public static class ExecContextStates {
        // key - execContextId, value - stae of execContext
        public final Map<Long, EnumsApi.ExecContextState> statuses = new HashMap<>();
    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RootAndParent {
        public Long rootExecContextId;
        public Long parentExecContextId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AssignedTaskComplex {
        public Long execContextId;
        public Task task;
    }

    @Data
    @EqualsAndHashCode(of = "taskId")
    @NoArgsConstructor
    public static class TaskVertex {
        public Long taskId;
        public String taskContextId;
        // A process tag carried into the DAG. Almost always null; set only for the rare process
        // that declared one in .mhsc (e.g. `tag terminal`). Serialized to the graph DOT only when
        // non-null (ExecContextGraphService.asString), so untagged vertices stay byte-identical.
        @org.jspecify.annotations.Nullable public String tag;

        public TaskVertex(Long taskId) {
            this.taskId = taskId;
        }

        public TaskVertex(Long taskId, String taskContextId) {
            this.taskId = taskId;
            this.taskContextId = taskContextId;
        }

        public TaskVertex(Long taskId, String taskContextId, @org.jspecify.annotations.Nullable String tag) {
            this.taskId = taskId;
            this.taskContextId = taskContextId;
            this.tag = tag;
        }
    }

    @Data
    @RequiredArgsConstructor
    public static class ReconciliationStatus {
        public final Long execContextId;
        public final AtomicBoolean isNullState = new AtomicBoolean(false);
        public final Set<Long> taskForResettingIds = new HashSet<>();
        public final List<Long> taskIsOkIds = new ArrayList<>();
    }
}

/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
package ai.metaheuristic.ai.yaml.processor_task;

import ai.metaheuristic.api.data.Meta;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@ToString
@NoArgsConstructor
public class ProcessorTask {

    // TODO 2020-12-30 'String dispatcherUrl' need to be changed it to DispatcherServerUrl when this class will be based on BaseClass

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(of = "variableId")
    public static class OutputStatus {
        // variableId of Variable.
        public Long variableId;

        // was a variable (an output variable as the result of execution of function) uploaded to dispatcher?
        public boolean uploaded;
    }

    @Data
    @NoArgsConstructor
    public static class Output {
        public final Set<OutputStatus> outputStatuses = new HashSet<>();

        @JsonIgnore
        public boolean allUploaded() {
            return outputStatuses.stream().allMatch(o->o.uploaded);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmptyStateOfInput {
        // variableId of Variable.
        public String variableId;
        public Boolean empty = null;
    }

    @Data
    @NoArgsConstructor
    public static class Empty {
        public final List<EmptyStateOfInput> empties = new ArrayList<>();

        @JsonIgnore
        public boolean isEmpty(String variableId) {
            return empties.stream().filter(o->o.variableId.equals(variableId)).findFirst().map(o-> Boolean.TRUE.equals(o.empty)).orElse(false);
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Quotas {
        public int quota;
    }

    public final Quotas quotas = new Quotas();

    public final Output output = new Output();
    public final Empty empty = new Empty();

    public Long taskId;

    public Long execContextId;

    // params of this task
    public String params;

    // function exec result
    // it contains data of FunctionApiData.FunctionExec in yaml format
    @Nullable
    public String functionExecResult;

    // need to clean a dir of task after processing this task?
    public boolean clean;

    // TODO 2020-12-30 need to change it to DispatcherServerUrl when this class will be based on BaseClass
    // TODO 2021-08-07 you mean ProcessorAndCoreData.DispatcherUrl()? and what the point of this change?
    public String dispatcherUrl;

    // when task was created
    public long createdOn;

    // were all assets (variables and functions) prepared?
    public boolean assetsPrepared;

    // when task was launched
    @Nullable
    public Long launchedOn;

    // when execution of function finished
    @Nullable
    public Long finishedOn;

    // when status and console output were reported to dispatcher
    @Nullable
    public Long reportedOn;

    // were status and console result reported to dispatcher?
    public boolean reported;

    // were status and console result received by dispatcher?
    public boolean delivered;

    // processing of this task was completed (it doesn't matter with which outcome)
    public boolean completed;

    // temporary storage for holding data - function's result and so on
    public List<Meta> metas = new ArrayList<>();
}

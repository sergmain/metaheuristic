/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@ToString
public class ProcessorTask {

    @Data
    public static class OutputStatus {
        // resourceId of Resource. Resource is a part of Variable
        public String resourceId;

        // was resource(output resource as the result of execution of function) uploaded to dispatcher?
        public boolean uploaded;
    }

    @Data
    public static class Output {
        public final List<OutputStatus> outputStatuses = new ArrayList<>();

        @JsonIgnore
        public boolean allUploaded() {
            return outputStatuses.stream().allMatch(o->o.uploaded);
        }
    }

    public final Output output = new Output();

    public long taskId;

    public Long execContextId;

    // params of this task
    public String params;

    // function exec result
    // it contains data of FunctionApiData.FunctionExec in yaml format
    public String functionExecResult;

    // need to clean a dir of task after processing this task?
    public boolean clean;

    public String dispatcherUrl;

    // when task was created
    public long createdOn;

    // were all assets (variables and functions) prepared?
    public boolean assetsPrepared;

    // when task was launched
    public Long launchedOn;

    // when execution of function finished
    public Long finishedOn;

    // when status and console output were reported to dispatcher
    public Long reportedOn;

    // was this task reported to dispatcher?
    public boolean reported;

    // were status and console result received by dispatcher?
    public boolean delivered;

    // processing of this task was completed (it doesn't matter with which outcome)
    public boolean completed;

    // temporary storage for holding data - function's result and so on
    public List<Meta> metas = new ArrayList<>();
}

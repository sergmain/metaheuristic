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
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@ToString
public class ProcessorTask {
    public long taskId;

    public Long execContextId;

    // params of this task
    public String params;

    // metrics of this task
    public String metrics;

    // function exec result
    public String functionExecResult;

    // need to clean a dir of task after processing this task?
    public boolean clean;

    public String dispatcherUrl;

    // when task was created
    public long createdOn;

    // were all assets (data resources and function) prepared?
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

    // was resource(output resource as the result of execution of function) uploaded to dispatcher?
    public boolean resourceUploaded;

    // processing of this task was completed (it doesn't matter with which outcome)
    public boolean completed;

    // temporary storage for holding data - function's result and so on
    public List<Meta> metas = new ArrayList<>();
}

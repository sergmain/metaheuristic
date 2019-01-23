/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package aiai.ai.yaml.station;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class StationTask {
    public long taskId;

    public long flowInstanceId;

    // params of this task
    public String params;

    // metrics of this task
    public String metrics;

    // snippet exec result
    public String snippetExecResult;

    // need to clean dir of task after processing this task?
    public boolean clean;

    public String launchpadUrl;

    // when task was created
    public long createdOn;

    // were all assets (data resources and snippet) prepared?
    public boolean assetsPrepared;

    // when task was launched
    public Long launchedOn;

    // when execution of snippet finished
    public Long finishedOn;

    // when status and console output were reported to launchpad
    public Long reportedOn;

    // was this task reported to launchpad?
    public boolean reported;

    // were status and console result received by launchpad?
    public boolean delivered;

    // was resource(output resource as the result of execution of snippet) uploaded to launchpad?
    public boolean resourceUploaded;

    // processing of this task was completed ( it doesn't matter with which outcome)
    public boolean completed;

}

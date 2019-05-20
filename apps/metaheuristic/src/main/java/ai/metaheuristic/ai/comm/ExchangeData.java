/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.comm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Serg
 * Date: 20.07.2017
 * Time: 18:58
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(value = {"commands"})
@ToString
public class ExchangeData {

    private Protocol.Nop nop;
    private Protocol.ReportStation reportStation;
    private Protocol.RequestTask requestTask;
    private Protocol.AssignedTask assignedTask;
    private Protocol.RequestStationId requestStationId;
    private Protocol.AssignedStationId assignedStationId;
    private Protocol.ReAssignStationId reAssignedStationId;
    private Protocol.ReportStationStatus reportStationStatus;
    private Protocol.ReportTaskProcessingResult reportTaskProcessingResult;
    private Protocol.ReportResultDelivering reportResultDelivering;
    private Protocol.WorkbookStatus workbookStatus;
    private Protocol.StationTaskStatus stationTaskStatus;
    private Protocol.CheckForMissingOutputResources checkForMissingOutputResources;
    private Protocol.ResendTaskOutputResource resendTaskOutputResource;
    private Protocol.ResendTaskOutputResourceResult resendTaskOutputResourceResult;

    @JsonProperty(value = "success")
    private boolean isSuccess = true;

    @JsonProperty(value = "station_id")
    private String stationId;

//    @JsonProperty(value = "session_id")
    private String sessionId;

    @JsonIgnore
    public String launchpadUrl;

    public ExchangeData() {
    }

    public ExchangeData(Command[] commands) {
        setCommands(commands);
    }

    public ExchangeData(Command command) {
        setCommand(command);
    }

    @JsonIgnore
    public void initRequestToLaunchpad(String stationId, String sessionId) {
        this.stationId = stationId;
        this.sessionId = sessionId;
    }

    @JsonIgnore
    public void setCommand(Command command) {
        command.setStationId(stationId);
        switch (command.getType()) {
            case Nop:
                this.nop = (Protocol.Nop) command;
                break;
            case ReportStation:
                if (this.reportStation != null) {
                    throw new IllegalStateException("Was already initialized");
                }
                this.reportStation = (Protocol.ReportStation) command;
                break;
            case WorkbookStatus:
                this.workbookStatus = (Protocol.WorkbookStatus) command;
                break;
            case StationTaskStatus:
                this.stationTaskStatus = (Protocol.StationTaskStatus) command;
                break;
            case CheckForMissingOutputResources:
                this.checkForMissingOutputResources = (Protocol.CheckForMissingOutputResources) command;
                break;
            case ResendTaskOutputResource:
                this.resendTaskOutputResource = (Protocol.ResendTaskOutputResource) command;
                break;
            case ResendTaskOutputResourceResult:
                this.resendTaskOutputResourceResult = (Protocol.ResendTaskOutputResourceResult) command;
                break;
            case RequestStationId:
                if (this.requestStationId != null) {
                    throw new IllegalStateException("Was already initialized");
                }
                this.requestStationId = (Protocol.RequestStationId) command;
                break;
            case AssignedStationId:
                this.assignedStationId = (Protocol.AssignedStationId) command;
                break;
            case ReAssignStationId:
                this.reAssignedStationId = (Protocol.ReAssignStationId) command;
                break;
            case RequestTask:
                if (this.requestTask != null) {
                    throw new IllegalStateException("Was already initialized");
                }
                this.requestTask = (Protocol.RequestTask) command;
                break;
            case AssignedTask:
                if (this.assignedTask != null && this.assignedTask.getTasks()!=null) {
                    this.assignedTask.getTasks().addAll( ((Protocol.AssignedTask) command).getTasks());
                    break;
                }
                this.assignedTask = (Protocol.AssignedTask) command;
                break;
            case ReportStationStatus:
                if (this.reportStationStatus != null) {
                    throw new IllegalStateException("Was already initialized");
                }
                this.reportStationStatus = (Protocol.ReportStationStatus) command;
                break;
            case ReportTaskProcessingResult:
                if (this.reportTaskProcessingResult != null) {
                    throw new IllegalStateException("Was already initialized");
                }
                this.reportTaskProcessingResult = (Protocol.ReportTaskProcessingResult) command;
                break;
            case ReportResultDelivering:
                if (this.reportResultDelivering != null) {
                    throw new IllegalStateException("Was already initialized");
                }
                this.reportResultDelivering = (Protocol.ReportResultDelivering) command;
                break;
        }
    }

    @JsonIgnore
    public List<Command> getCommands() {
        return getCommands(false);
    }

    @JsonIgnore
    public void setCommands(List<Command> commands) {
        for (Command command : commands) {
            setCommand(command);
        }
    }

    @JsonIgnore
    public void setCommands(Command[] commands) {
        for (Command command : commands) {
            setCommand(command);
        }
    }

    @JsonIgnore
    public List<Command> getCommands(boolean isExcludeNop) {
        return asListOfNonNull(isExcludeNop, nop, reportStation, requestStationId,
                assignedStationId, reAssignedStationId, requestTask, assignedTask, reportStationStatus,
                reportTaskProcessingResult, reportResultDelivering, workbookStatus, stationTaskStatus,
                checkForMissingOutputResources, resendTaskOutputResource, resendTaskOutputResourceResult);
    }

    @JsonIgnore
    public boolean isNothingTodo() {
        final List<Command> commands = getCommands(true);
        return commands.isEmpty();
    }

    @JsonIgnore
    private static List<Command> asListOfNonNull(boolean isExcludeNop, Command... commands) {
        List<Command> list = new ArrayList<>();
        for (Command command : commands) {
            if (command == null) {
                continue;
            }
            if (isExcludeNop && command.getType() == Command.Type.Nop) {
                continue;
            }
            list.add(command);
        }
        return list;
    }
}

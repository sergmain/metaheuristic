/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
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

package aiai.ai.comm;

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
    private Protocol.RegisterInvite registerInvite;
    private Protocol.RegisterInviteResult registerInviteResult;
    private Protocol.ReportStationStatus reportStationStatus;
    private Protocol.ReportSequenceProcessingResult reportSequenceProcessingResult;
    private Protocol.ReportResultDelivering reportResultDelivering;
    private Protocol.ExperimentStatus experimentStatus;
    private Protocol.StationSequenceStatus stationSequenceStatus;

    @JsonProperty(value = "success")
    private boolean isSuccess = true;

    @JsonProperty(value = "station_id")
    private String stationId;

    public ExchangeData() {
    }

    public ExchangeData(Command[] commands) {
        setCommands(commands);
    }

    public ExchangeData(Command command) {
        setCommand(command);
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
            case ExperimentStatus:
                this.experimentStatus = (Protocol.ExperimentStatus) command;
                break;
            case StationSequenceStatus:
                this.stationSequenceStatus = (Protocol.StationSequenceStatus) command;
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
            case RegisterInvite:
                if (this.registerInvite != null) {
                    throw new IllegalStateException("Was already initialized");
                }
                this.registerInvite = (Protocol.RegisterInvite) command;
                break;
            case RegisterInviteResult:
                if (this.registerInviteResult != null) {
                    throw new IllegalStateException("Was already initialized");
                }
                this.registerInviteResult = (Protocol.RegisterInviteResult) command;
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
            case ReportSequenceProcessingResult:
                if (this.reportSequenceProcessingResult != null) {
                    throw new IllegalStateException("Was already initialized");
                }
                this.reportSequenceProcessingResult = (Protocol.ReportSequenceProcessingResult) command;
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
                assignedStationId, reAssignedStationId, registerInvite,
                registerInviteResult, requestTask, assignedTask, reportStationStatus,
                reportSequenceProcessingResult, reportResultDelivering, experimentStatus, stationSequenceStatus);
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

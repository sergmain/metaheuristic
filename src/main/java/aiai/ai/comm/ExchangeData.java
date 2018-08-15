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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

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
public class ExchangeData {

    private Protocol.Nop nop;
    private Protocol.ReportStation reportStation;
    private Protocol.RequestExperimentSequence requestExperimentSequence;
    private Protocol.AssignedExperimentSequence assignedExperimentSequence;
    private Protocol.RequestStationId requestStationId;
    private Protocol.AssignedStationId assignedStationId;
    private Protocol.ReAssignStationId reAssignedStationId;
    private Protocol.RegisterInvite registerInvite;
    private Protocol.RegisterInviteResult registerInviteResult;

    @JsonProperty(value = "success")
    private boolean isSuccess = true;
    private String msg;

    @JsonProperty(value = "station_id")
    private String stationId;


    public ExchangeData() {
    }

    public ExchangeData(Command command) {
        setCommand(command);
    }

    public ExchangeData(boolean isSuccess, String msg) {
        this.isSuccess = isSuccess;
        this.msg = msg;
    }

    static List<Command> asListOfNonNull(Command... commands) {
        List<Command> list = new ArrayList<>();
        for (Command command : commands) {
            if (command != null) {
                list.add(command);
            }
        }
        return list;
    }

    public void setCommands(List<Command> commands) {
        for (Command command : commands) {
            setCommand(command);
        }
    }

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
            case RequestExperimentSequence:
                if (this.requestExperimentSequence != null) {
                    throw new IllegalStateException("Was already initialized");
                }
                this.requestExperimentSequence = (Protocol.RequestExperimentSequence) command;
                break;
            case AssignedExperimentSequence:
                if (this.assignedExperimentSequence != null) {
                    throw new IllegalStateException("Was already initialized");
                }
                this.assignedExperimentSequence = (Protocol.AssignedExperimentSequence) command;
                break;
        }
    }

    public List<Command> getCommands() {
        return asListOfNonNull(
                nop, reportStation, requestStationId,
                assignedStationId, reAssignedStationId, registerInvite,
                registerInviteResult, requestExperimentSequence, assignedExperimentSequence
        );
    }

    @Override
    public String toString() {
        return "ExchangeData{" +
                "commands=" + getCommands() +
                '}';
    }
}

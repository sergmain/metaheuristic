/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.commands;

import aiai.ai.comm.Command;
import aiai.ai.comm.ExchangeData;
import aiai.ai.comm.Protocol;
import org.junit.Assert;
import org.junit.Test;

public class TestCommands {

    private static Command getCommandInstance(Command.Type type) {
        switch (type) {
            case Nop:
                return new Protocol.Nop();
            case ReportStation:
                return new Protocol.ReportStation();
            case RequestStationId:
                return new Protocol.RequestStationId();
            case AssignedStationId:
                return new Protocol.AssignedStationId();
            case ReAssignStationId:
                return new Protocol.ReAssignStationId();
            case RegisterInvite:
                return new Protocol.RegisterInvite();
            case RegisterInviteResult:
                return new Protocol.RegisterInviteResult();
            case RequestExperimentSequence:
                return new Protocol.RequestExperimentSequence();
            case AssignedExperimentSequence:
                return new Protocol.AssignedExperimentSequence();
            default:
                throw new IllegalStateException("unknown command type: " + type);
        }
    }

    @Test
    public void testCommandsIntegrity() {
        final ExchangeData data = new ExchangeData();
        for (Command.Type value : Command.Type.values()) {
            data.setCommand(getCommandInstance(value));
        }
        Assert.assertEquals(Command.Type.values().length, data.getCommands().size());
    }

}

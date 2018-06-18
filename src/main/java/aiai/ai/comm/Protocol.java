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

import aiai.ai.beans.InviteResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 22:20
 */
public class Protocol {

    /**
     * stub command, which is actually doing nothing
     */
    public static class Nop extends Command {
        public Nop() {
            this.setType(Type.Nop);
        }
    }

    /**
     * another stub command, which is actually doing nothing
     */
    public static class Ok extends Command {
        public Ok() {
            this.setType(Type.Ok);
        }
    }

    public static class AssignStationId extends Command {
        public AssignStationId() {
            this.setType(Type.AssignStationId);
        }
    }

    public static class ReportStation extends Command {
        public ReportStation() {
            this.setType(Type.ReportStation);
        }
    }

    public static class RequestDefinitions extends Command {
        public RequestDefinitions() {
            this.setType(Type.RequestDefinitions);
        }
    }

    public static class RequestExperiment extends Command {
        public RequestExperiment() {
            this.setType(Type.RequestExperiment);
        }
    }

    @EqualsAndHashCode(callSuper = false)
    @Data
    public static class RegisterInvite extends Command {
        private String invite;

        public RegisterInvite(String invite) {
            this.setType(Type.RegisterInvite);
            this.invite = invite;
        }

        public RegisterInvite() {
            this.setType(Type.RegisterInvite);
        }
    }

    @EqualsAndHashCode(callSuper = false)
    @Data
    public static class RegisterInviteResult extends Command {
        private InviteResult inviteResult;

        public RegisterInviteResult() {
            this.setType(Type.RegisterInviteResult);
        }
    }


}

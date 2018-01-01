package aiai.ai.comm;

import aiai.ai.beans.InviteResult;
import lombok.Data;

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

    public static class RequestDatasets extends Command {
        public RequestDatasets() {
            this.setType(Type.RequestDatasets);
        }
    }

    public static class RequestExperiment extends Command {
        public RequestExperiment() {
            this.setType(Type.RequestDatasets);
        }
    }

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

    @Data
    public static class RegisterInviteResult extends Command {
        private InviteResult inviteResult;
        public RegisterInviteResult() {
            this.setType(Type.RegisterInviteResult);
        }
    }




}

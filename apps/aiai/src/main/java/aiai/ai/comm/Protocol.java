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

import aiai.ai.Enums;
import aiai.ai.launchpad.beans.InviteResult;
import aiai.ai.launchpad.experiment.SimpleSequenceExecResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import javax.persistence.Transient;
import java.util.ArrayList;
import java.util.List;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 22:20
 */
public class Protocol {

    public static final Protocol.Nop NOP = new Nop();
    public static final Command[] NOP_ARRAY = new Nop[]{NOP};

    /**
     * stub command, which is actually doing nothing
     */
    @EqualsAndHashCode(callSuper = false)
    public static class Nop extends Command {
        public Nop() {
            this.setType(Type.Nop);
        }
    }

    @EqualsAndHashCode(callSuper = false)
    public static class RequestStationId extends Command {
        public RequestStationId() {
            this.setType(Type.RequestStationId);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class ExperimentStatus extends Command {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SimpleStatus {
            public long experimentId;
            public Enums.ExperimentExecState state;
        }
        List<SimpleStatus> statuses;

        public ExperimentStatus(List<SimpleStatus> statuses) {
            this.setType(Type.ExperimentStatus);
            this.statuses = statuses;
        }

        public ExperimentStatus() {
            this.setType(Type.ExperimentStatus);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class StationSequenceStatus extends Command {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        // will use static inner class for future extension
        public static class SimpleStatus {
            public long experimentSequenceId;
        }
        List<SimpleStatus> statuses;

        public StationSequenceStatus(List<SimpleStatus> statuses) {
            this.setType(Type.StationSequenceStatus);
            this.statuses = statuses;
        }

        public StationSequenceStatus() {
            this.setType(Type.StationSequenceStatus);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class AssignedTask extends Command {
        @Data
        public static class Sequence {
            public String params;
            public Long experimentSequenceId;
        }
        @Data
        public static class RawAssembling {
            public String params;
            public Long datasetId;
        }
        @Data
        public static class DatasetProducing {
            public String params;
            public Long datasetId;
        }
        List<Sequence> sequences;
        List<RawAssembling> rawAssemblings;
        List<DatasetProducing> datasetProducings;

        public AssignedTask() {
            this.setType(Type.AssignedTask);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class AssignedStationId extends Command {
        public String assignedStationId;

        public AssignedStationId(String assignedStationId) {
            this.setType(Type.AssignedStationId);
            this.assignedStationId = assignedStationId;
        }

        public AssignedStationId() {
            this.setType(Type.AssignedStationId);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class ReportStationStatus extends Command {
        public String env;
        public String activeTime;

        public ReportStationStatus(String env, String activeTime) {
            this.setType(Type.ReportStationStatus);
            this.env = env;
            this.activeTime = activeTime;
        }

        @Transient
        public boolean isOkToReport() {
            return StringUtils.isNotBlank(env) || StringUtils.isNotBlank(activeTime);
        }

        public ReportStationStatus() {
            this.setType(Type.ReportStationStatus);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class ReAssignStationId extends Command {
        String reAssignedStationId;

        public ReAssignStationId(Long stationId) {
            this(Long.toString(stationId));
        }

        public ReAssignStationId(String reAssignedStationId) {
            this.setType(Type.ReAssignStationId);
            this.reAssignedStationId = reAssignedStationId;
        }

        public ReAssignStationId() {
            this.setType(Type.ReAssignStationId);
        }
    }

    public static class ReportStation extends Command {
        public ReportStation() {
            this.setType(Type.ReportStation);
        }
    }

    @EqualsAndHashCode(callSuper = false)
    @Data
    public static class RequestTask extends Command {
        private boolean isAcceptOnlySigned;

        public RequestTask(boolean isAcceptOnlySigned) {
            this.setType(Type.RequestTask);
            this.isAcceptOnlySigned = isAcceptOnlySigned;
        }

        public RequestTask() {
            this.setType(Type.RequestTask);
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

    @EqualsAndHashCode(callSuper = false)
    @Data
    public static class ReportSequenceProcessingResult extends Command {
        private List<SimpleSequenceExecResult> results = new ArrayList<>();

        public ReportSequenceProcessingResult() {
            this.setType(Type.ReportSequenceProcessingResult);
        }
    }

    @EqualsAndHashCode(callSuper = false)
    @Data
    public static class ReportResultDelivering extends Command {
        private List<Long> ids = new ArrayList<>();

        public ReportResultDelivering() {
            this.setType(Type.ReportResultDelivering);
        }

        public ReportResultDelivering(List<Long> ids) {
            this.setType(Type.ReportResultDelivering);
            this.ids.addAll(ids);
        }
    }

    static Command[] asArray(Command cmd) {
        return new Command[]{cmd};
    }
}

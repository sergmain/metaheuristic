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
import aiai.ai.launchpad.experiment.SimpleTaskExecResult;
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

    @EqualsAndHashCode(callSuper = false)
    public static class CheckForMissingOutputResources extends Command {
        public CheckForMissingOutputResources() {
            this.setType(Type.CheckForMissingOutputResources);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class ResendTaskOutputResource extends Command {
        public List<Long> taskIds;

        public ResendTaskOutputResource() {
            this.setType(Type.ResendTaskOutputResource);
        }

        public ResendTaskOutputResource(List<Long> taskIds) {
            this.taskIds = taskIds;
            this.setType(Type.ResendTaskOutputResource);
        }

    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class ResendTaskOutputResourceResult extends Command {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SimpleStatus {
            public long taskId;
            public Enums.ResendTaskOutputResourceStatus status;
        }

        List<SimpleStatus> statuses;

        public ResendTaskOutputResourceResult() {
            this.setType(Type.ResendTaskOutputResourceResult);
        }

        public ResendTaskOutputResourceResult(List<SimpleStatus> statuses) {
            this.statuses = statuses;
            this.setType(Type.ResendTaskOutputResourceResult);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class FlowInstanceStatus extends Command {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SimpleStatus {
            public long flowInstanceId;
            public Enums.FlowInstanceExecState state;
        }
        List<SimpleStatus> statuses;

        public FlowInstanceStatus(List<SimpleStatus> statuses) {
            this.setType(Type.FlowInstanceStatus);
            this.statuses = statuses;
        }

        public FlowInstanceStatus() {
            this.setType(Type.FlowInstanceStatus);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class StationTaskStatus extends Command {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        // will use static inner class for future extension
        public static class SimpleStatus {
            public long experimentSequenceId;
        }
        List<SimpleStatus> statuses;

        public StationTaskStatus(List<SimpleStatus> statuses) {
            this.setType(Type.StationTaskStatus);
            this.statuses = statuses;
        }

        public StationTaskStatus() {
            this.setType(Type.StationTaskStatus);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class AssignedTask extends Command {
        @Data
        public static class Task {
            public String params;
            public Long taskId;
            public Long flowInstanceId;
        }
        public List<Task> tasks;

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
    public static class ReportTaskProcessingResult extends Command {
        private List<SimpleTaskExecResult> results = new ArrayList<>();

        public ReportTaskProcessingResult() {
            this.setType(Type.ReportTaskProcessingResult);
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

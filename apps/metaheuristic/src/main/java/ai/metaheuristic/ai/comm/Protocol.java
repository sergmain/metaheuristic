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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.launchpad.experiment.task.SimpleTaskExecResult;
import ai.metaheuristic.ai.station.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.env.EnvYaml;
import ai.metaheuristic.ai.yaml.station_status.StationStatus;
import ai.metaheuristic.api.v1.EnumsApi;
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
    static final Command[] NOP_ARRAY = new Nop[]{NOP};

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
    public static class WorkbookStatus extends Command {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SimpleStatus {
            public long workbookId;
            public EnumsApi.WorkbookExecState state;
        }
        List<SimpleStatus> statuses;

        public WorkbookStatus(List<SimpleStatus> statuses) {
            this.setType(Type.WorkbookStatus);
            this.statuses = statuses;
        }

        public WorkbookStatus() {
            this.setType(Type.WorkbookStatus);
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
            public long taskId;
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
            public Long workbookId;
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
        public String assignedSessionId;

        public AssignedStationId(String assignedStationId, String assignedSessionId) {
            this.setType(Type.AssignedStationId);
            this.assignedStationId = assignedStationId;
            this.assignedSessionId = assignedSessionId;
        }

        public AssignedStationId() {
            this.setType(Type.AssignedStationId);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class ReportStationStatus extends Command {
        public StationStatus status;

        public ReportStationStatus(EnvYaml env, String schedule, GitSourcingService.GitStatusInfo gitStatusInfo, String sessionId) {
            this.setType(Type.ReportStationStatus);
            this.status = new StationStatus(env, gitStatusInfo, schedule, sessionId, System.currentTimeMillis());
        }

        @Transient
        @Deprecated(forRemoval = true)
        public boolean isOkToReport() {
            return status.env!=null || StringUtils.isNotBlank(status.schedule);
        }

        public ReportStationStatus() {
            this.setType(Type.ReportStationStatus);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class ReAssignStationId extends Command {
        public String reAssignedStationId;
        public String sessionId;

        public ReAssignStationId(Long stationId, String sessionId) {
            this(Long.toString(stationId), sessionId);
        }

        public ReAssignStationId(String reAssignedStationId, String sessionId) {
            this.setType(Type.ReAssignStationId);
            this.reAssignedStationId = reAssignedStationId;
            this.sessionId = sessionId;
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

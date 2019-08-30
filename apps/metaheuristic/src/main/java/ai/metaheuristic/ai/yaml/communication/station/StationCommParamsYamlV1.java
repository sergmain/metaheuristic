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

package ai.metaheuristic.ai.yaml.communication.station;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.station.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.env.EnvYaml;
import ai.metaheuristic.api.data.BaseParams;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.beans.Transient;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 8/29/2019
 * Time: 6:00 PM
 */
public class StationCommParamsYamlV1 implements BaseParams {

    @Override
    public boolean checkIntegrity() {
/*
        final boolean b = planYaml != null && planYaml.planCode != null && !planYaml.planCode.isBlank() &&
                planYaml.processes != null;
        if (!b) {
            throw new IllegalArgumentException(
                    "(boolean b = planYaml != null && planYaml.planCode != null && " +
                            "!planYaml.planCode.isBlank() && planYaml.processes != null) ");
        }
        for (ProcessV5 process : planYaml.processes) {
            if (process.snippets == null || process.snippets.size() == 0) {
                throw new IllegalArgumentException("(process.snippets==null || process.snippets.size()==0) ");
            }
        }
*/

        return true;
    }

    public StationCommContextV1 stationCommContext;
    public RequestStationIdV1 requestStationId;
    public ReportStationStatusV1 reportStationStatus;
    public ReportStationTaskStatusV1 reportStationTaskStatus;
    public RequestTaskV1 requestTask;
    public ReportTaskProcessingResultV1 reportTaskProcessingResult;
    public CheckForMissingOutputResourcesV1 checkForMissingOutputResources;
    public ResendTaskOutputResourceResultV1 resendTaskOutputResourceResult;


    public static class StationCommContextV1 {
        private String stationId;
        private String sessionId;
    }

    @Data
    public static class RequestStationIdV1 {
        public boolean keep = true;
    }

    @Data
    public static class CheckForMissingOutputResourcesV1 {
        public boolean keep = true;
    }

    @Data
    public static class RequestTaskV1 {
        private boolean isAcceptOnlySigned;
    }

    @Data
    public static class ReportStationStatusV1 {
        public EnvYaml env;
        public GitSourcingService.GitStatusInfo gitStatusInfo;
        public String schedule;
        public String sessionId;

        // TODO 2019-05-28, a multi-time-zoned deployment isn't supported right now
        // it'll work but in some cases behaviour can be different
        // need to change it to UTC, Coordinated Universal Time
        public long sessionCreatedOn;
        public String ip;
        public String host;

        // contains text of error which can occur while preparing a station status
        public List<String> errors = null;
        public boolean logDownloadable;
        public int taskParamsVersion;

        public void addError(String error) {
            if (errors==null) {
                errors = new ArrayList<>();
            }
            errors.add(error);
        }
    }

    @Data
    public static class ReportTaskProcessingResultV1 {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public class SimpleTaskExecResult {
            public long taskId;
            public String result;
            public String metrics;
        }

        private List<StationCommParamsYamlV1.ReportTaskProcessingResultV1.SimpleTaskExecResult> results = new ArrayList<>();
    }

    @Data
    public static class ReportStationTaskStatusV1 {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SimpleStatus {
            public long taskId;
        }

        List<StationCommParamsYaml.ReportStationTaskStatus.SimpleStatus> statuses;
    }

    @Data
    public static class ResendTaskOutputResourceResultV1 {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SimpleStatus {
            public long taskId;
            public Enums.ResendTaskOutputResourceStatus status;
        }

        List<StationCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus> statuses;

        public ResendTaskOutputResourceResultV1(List<StationCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus> statuses) {
            this.statuses = statuses;
        }
    }

    @JsonIgnore
    private String launchpadUrl;

    @Transient
    public String getLaunchpadUrl() {
        return launchpadUrl;
    }

    @Transient
    public void setLaunchpadUrl(String launchpadUrl) {
        this.launchpadUrl = launchpadUrl;
    }

    public final int version=1;
}
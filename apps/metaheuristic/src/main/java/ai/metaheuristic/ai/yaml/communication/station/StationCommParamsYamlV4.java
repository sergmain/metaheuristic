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
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.data.Meta;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Communication file which is transferred from a Station to Launchpad
 *
 * @author Serge
 * Date: 11/13/2019
 * Time: 6:00 PM
 */
@Data
@NoArgsConstructor
public class StationCommParamsYamlV4 implements BaseParams {

    public final int version=4;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    public SnippetDownloadStatusV4 snippetDownloadStatus;
    public StationCommContextV4 stationCommContext;
    public RequestStationIdV4 requestStationId;
    public ReportStationStatusV4 reportStationStatus;
    public ReportStationTaskStatusV4 reportStationTaskStatus;
    public RequestTaskV4 requestTask;
    public ReportTaskProcessingResultV4 reportTaskProcessingResult;
    public CheckForMissingOutputResourcesV4 checkForMissingOutputResources;
    public ResendTaskOutputResourceResultV4 resendTaskOutputResourceResult;

    @Data
    public static class SnippetDownloadStatusV4 {
        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class Status {
            public Enums.SnippetState snippetState;
            public String snippetCode;
        }

        public List<Status> statuses = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StationCommContextV4 {
        public String stationId;
        public String sessionId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RequestStationIdV4 {
        public boolean keep = true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CheckForMissingOutputResourcesV4 {
        public boolean keep = true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RequestTaskV4 {
        public boolean acceptOnlySigned;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportStationStatusV4 {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SnippetStatus {
            public String code;
            public Enums.SnippetState state;
        }

        public List<SnippetStatus> snippetStatuses = null;

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

        public EnumsApi.OS os;

        public void addError(String error) {
            if (errors==null) {
                errors = new ArrayList<>();
            }
            errors.add(error);
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReportTaskProcessingResultV4 {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class MachineLearningTaskResult {
            public String metrics;
            public String predicted;
            public EnumsApi.Fitting fitting;
        }

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SimpleTaskExecResult {
            public long taskId;
            public String result;
            public MachineLearningTaskResult ml;
        }

        public List<SimpleTaskExecResult> results = new ArrayList<>();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReportStationTaskStatusV4 {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SimpleStatus {
            public long taskId;
        }

        public List<SimpleStatus> statuses;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResendTaskOutputResourceResultV4 {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SimpleStatus {
            public long taskId;
            public Enums.ResendTaskOutputResourceStatus status;
        }

        public List<SimpleStatus> statuses;
    }
}

/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.yaml.communication.launchpad;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Communication file which is transferred from a Launchpad to a Station
 *
 * @author Serge
 * Date: 10/03/2019
 * Time: 6:00 PM
 */
@Data
public class LaunchpadCommParamsYamlV2 implements BaseParams {

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    public LaunchpadCommContextV2 launchpadCommContext;

    public SnippetsV2 snippets;
    public AssignedTaskV2 assignedTask;
    public AssignedStationIdV2 assignedStationId;
    public ReAssignStationIdV2 reAssignedStationId;
    public ReportResultDeliveringV2 reportResultDelivering;
    public WorkbookStatusV2 workbookStatus;
    public ResendTaskOutputResourceV2 resendTaskOutputResource;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SnippetsV2 {
        @Data
        public static class Info {
            public String code;
            public EnumsApi.SnippetSourcing sourcing;
        }
        public List<Info> infos = new ArrayList<>();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AssignedTaskV2 {
        public String params;
        public Long taskId;
        public Long workbookId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignedStationIdV2 {
        public String assignedStationId;
        public String assignedSessionId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReAssignStationIdV2 {
        public String reAssignedStationId;
        public String sessionId;

        public ReAssignStationIdV2(Long stationId, String sessionId) {
            this(Long.toString(stationId), sessionId);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportResultDeliveringV2 {
        public List<Long> ids;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkbookStatusV2 {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SimpleStatus {
            public long workbookId;
            public EnumsApi.WorkbookExecState state;
        }

        public List<SimpleStatus> statuses;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResendTaskOutputResourceV2 {
        public List<Long> taskIds;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LaunchpadCommContextV2 {
        public Long chunkSize;
        // Station's version for communicating with launchpad
        public Integer stationCommVersion;
    }

    public boolean success = true;
    public String msg;

    public final int version=2;
}

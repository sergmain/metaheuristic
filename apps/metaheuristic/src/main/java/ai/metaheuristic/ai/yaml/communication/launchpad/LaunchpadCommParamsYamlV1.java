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

import java.util.List;

/**
 * Communication file which is transferred from a Launchpad to a Station
 *
 * @author Serge
 * Date: 8/29/2019
 * Time: 6:00 PM
 */
@Data
public class LaunchpadCommParamsYamlV1 implements BaseParams {

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

    public LaunchpadCommContextV1 launchpadCommContext;

    public AssignedTaskV1 assignedTask;
    public AssignedStationIdV1 assignedStationId;
    public ReAssignStationIdV1 reAssignedStationId;
    public ReportResultDeliveringV1 reportResultDelivering;
    public WorkbookStatusV1 workbookStatus;
    public ResendTaskOutputResourceV1 resendTaskOutputResource;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AssignedTaskV1 {
        public String params;
        public Long taskId;
        public Long workbookId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignedStationIdV1 {
        public String assignedStationId;
        public String assignedSessionId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReAssignStationIdV1 {
        public String reAssignedStationId;
        public String sessionId;

        public ReAssignStationIdV1(Long stationId, String sessionId) {
            this(Long.toString(stationId), sessionId);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportResultDeliveringV1 {
        public List<Long> ids;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkbookStatusV1 {

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
    public static class ResendTaskOutputResourceV1 {
        public List<Long> taskIds;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LaunchpadCommContextV1 {
        public Long chunkSize;
    }

    public boolean success = true;
    public String msg;

    public final int version=1;
}

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

package ai.metaheuristic.ai.yaml.communication.launchpad;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Config file which is transferred from a Launchpad to a Station
 *
 * @author Serge
 * Date: 8/29/2019
 * Time: 6:00 PM
 */
@Data
public class LaunchpadCommParamsYaml implements BaseParams {

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

    public LaunchpadCommContext launchpadCommContext;

    // always send info about snippets
    public Snippets snippets = new Snippets();
    public AssignedTask assignedTask;
    public AssignedStationId assignedStationId;
    public ReAssignStationId reAssignedStationId;
    public ReportResultDelivering reportResultDelivering;
    public WorkbookStatus workbookStatus;
    public ResendTaskOutputResource resendTaskOutputResource;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Snippets {
        public List<String> codes = new ArrayList<>();
    }

    @Data
    public static class AssignedTask {
        public String params;
        public Long taskId;
        public Long workbookId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignedStationId {
        public String assignedStationId;
        public String assignedSessionId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReAssignStationId {
        public String reAssignedStationId;
        public String sessionId;

        public ReAssignStationId(Long stationId, String sessionId) {
            this(Long.toString(stationId), sessionId);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportResultDelivering {
        public List<Long> ids;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkbookStatus {

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
    public static class ResendTaskOutputResource {
        public List<Long> taskIds;
    }

    public static class LaunchpadCommContext {
        public Long chunkSize;
    }

    public boolean success = true;
    public String msg;

    public final int version=2;
}

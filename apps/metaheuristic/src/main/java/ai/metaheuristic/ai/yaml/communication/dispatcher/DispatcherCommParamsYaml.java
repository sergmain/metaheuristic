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

package ai.metaheuristic.ai.yaml.communication.dispatcher;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Communication file which is transferred from a dispatcher to a Station
 *
 * @author Serge
 * Date: 8/29/2019
 * Time: 6:00 PM
 */
@Data
public class DispatcherCommParamsYaml implements BaseParams {

    public final int version=1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    public DispatcherCommContext dispatcherCommContext;

    // always send info about functions
    public Functions functions = new Functions();
    public AssignedTask assignedTask;
    public AssignedStationId assignedStationId;
    public ReAssignStationId reAssignedStationId;
    public ReportResultDelivering reportResultDelivering;
    public ExecContextStatus execContextStatus;
    public ResendTaskOutputResource resendTaskOutputResource;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Functions {
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Info {
            public String code;
            public EnumsApi.FunctionSourcing sourcing;
        }
        public List<Info> infos = new ArrayList<>();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AssignedTask {
        public String params;
        public Long taskId;
        public Long execContextId;
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
    public static class ExecContextStatus {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SimpleStatus {
            public long execContextId;
            public EnumsApi.ExecContextState state;
        }

        public List<SimpleStatus> statuses;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResendTaskOutputResource {
        public List<Long> taskIds;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DispatcherCommContext {
        public Long chunkSize;
        // Station's version for communicating with Dispatcher
        public Integer stationCommVersion;
    }

    public boolean success = true;
    public String msg;
}

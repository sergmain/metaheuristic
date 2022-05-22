/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.yaml.communication.keep_alive;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 11/21/2020
 * Time: 1:11 AM
 */
@Data
public class KeepAliveResponseParamYaml implements BaseParams {

    public final int version=2;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @NoArgsConstructor
    public static class Functions {
        public final Map<EnumsApi.FunctionSourcing, String> infos = new HashMap<>();
    }

    @Data
    @NoArgsConstructor
    public static class ExecContextStatus {
        // key - stae of execContext, value - comma-separated list of execContextId
        public final Map<EnumsApi.ExecContextState, String> statuses = new HashMap<>();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DispatcherInfo {
        public Long chunkSize;
        // Processor's version for communicating with Dispatcher
        public Integer processorCommVersion;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RequestLogFile {
        public long requestedOn;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignedProcessorId {
        public Long assignedProcessorId;
        public String assignedSessionId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReAssignedProcessorId {
        public String reAssignedProcessorId;
        public String sessionId;

        public ReAssignedProcessorId(Long processorId, String sessionId) {
            this(Long.toString(processorId), sessionId);
        }
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReAssignedProcessorCoreIdV2 {
        public String reAssignedProcessorCoreId;

        public ReAssignedProcessorCoreIdV2(Long processorCoreId) {
            this.reAssignedProcessorCoreId = Long.toString(processorCoreId);
        }
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoreInfo {
        public Long coreId;
        public String code;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispatcherResponse {
        public String processorCode;

        @Nullable
        public ReAssignedProcessorId reAssignedProcessorId;

        @Nullable
        public AssignedProcessorId assignedProcessorId;

        public final List<CoreInfo> coreInfos = new ArrayList<>();

        @Nullable
        public RequestLogFile requestLogFile;

        public DispatcherResponse(String processorCode) {
            this.processorCode = processorCode;
        }
    }

    public final DispatcherResponse response = new DispatcherResponse();
    public final Functions functions = new Functions();
    public ExecContextStatus execContextStatus = new ExecContextStatus();
    public DispatcherInfo dispatcherInfo;

    public boolean success = true;
    public String msg;

}

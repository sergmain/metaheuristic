/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
 * Date: 04/30/2022
 * Time: 1:11 AM
 */
@Data
public class KeepAliveResponseParamYamlV2 implements BaseParams {

    public final int version=2;

    @Data
    @NoArgsConstructor
    public static class ExecContextStatusV2 {
        // key - stae of execContext, value - comma-separated list of execContextId
        public final Map<EnumsApi.ExecContextState, String> statuses = new HashMap<>();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DispatcherInfoV2 {
        public Long chunkSize;
        // Processor's version for communicating with Dispatcher
        public Integer processorCommVersion;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RequestLogFileV2 {
        public long requestedOn;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignedProcessorIdV2 {
        public Long assignedProcessorId;
        public String assignedSessionId;
    }
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReAssignedProcessorIdV2 {
        public String reAssignedProcessorId;
        public String sessionId;
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
    public static class CoreInfoV2 {
        public Long coreId;
        public String code;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispatcherResponseV2 {
        public String processorCode;

        @Nullable
        public ReAssignedProcessorIdV2 reAssignedProcessorId;

        @Nullable
        public AssignedProcessorIdV2 assignedProcessorId;

        public final List<CoreInfoV2> coreInfos = new ArrayList<>();

        @Nullable
        public RequestLogFileV2 requestLogFile;

        public DispatcherResponseV2(String processorCode) {
            this.processorCode = processorCode;
        }
    }

    public final DispatcherResponseV2 response = new DispatcherResponseV2();
    public final ExecContextStatusV2 execContextStatus = new ExecContextStatusV2();
    public DispatcherInfoV2 dispatcherInfo;

    public boolean success = true;
    public String msg;

}

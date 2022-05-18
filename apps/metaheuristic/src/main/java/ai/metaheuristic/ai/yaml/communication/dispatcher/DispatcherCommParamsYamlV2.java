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

package ai.metaheuristic.ai.yaml.communication.dispatcher;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Communication file which is transferred from a dispatcher to a Processor
 *
 * @author Serge
 * Date: 10/03/2019
 * Time: 6:00 PM
 */
@Data
public class DispatcherCommParamsYamlV2 implements BaseParams {

    public final int version=1;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AssignedTaskV2 {
        public String params;
        public Long taskId;
        public Long execContextId;
        public EnumsApi.ExecContextState state;
        public String tag;
        public int quota;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignedProcessorIdV2 {
        public String assignedProcessorId;
        public String assignedSessionId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReAssignProcessorIdV2 {
        public String reAssignedProcessorId;
        public String sessionId;
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
    public static class ResendTaskOutputV2 {
        public Long taskId;
        public Long variableId;
    }

    @Data
    @NoArgsConstructor
    public static class ResendTaskOutputsV2 {
        public final List<ResendTaskOutputV2> resends = new ArrayList<>();
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
    public static class CoreV2 {
        public String code;
        @Nullable
        public Long coreId;
        @Nullable
        public DispatcherCommParamsYaml.AssignedTask assignedTask;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispatcherResponseV2 {
        @Nullable
        public AssignedTaskV2 assignedTask;
        @Nullable
        public AssignedProcessorIdV2 assignedProcessorId;
        @Nullable
        public ReAssignProcessorIdV2 reAssignedProcessorId;
        @Nullable
        public ReportResultDeliveringV2 reportResultDelivering;
        @Nullable
        public ResendTaskOutputsV2 resendTaskOutputs;

        public final List<CoreV2> cores = new ArrayList<>();
    }

    public final DispatcherResponseV2 response = new DispatcherResponseV2();
    
    @Nullable
    public RequestLogFileV2 requestLogFile;

    public boolean success = true;
    public String msg;
}

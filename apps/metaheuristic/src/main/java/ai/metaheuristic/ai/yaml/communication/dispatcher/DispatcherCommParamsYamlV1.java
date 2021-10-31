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
public class DispatcherCommParamsYamlV1 implements BaseParams {

    public final int version=1;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AssignedTaskV1 {
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
    public static class AssignedProcessorIdV1 {
        public String assignedProcessorId;
        public String assignedSessionId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReAssignProcessorIdV1 {
        public String reAssignedProcessorId;
        public String sessionId;

        public ReAssignProcessorIdV1(Long processorId, String sessionId) {
            this(Long.toString(processorId), sessionId);
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
    public static class ResendTaskOutputV1 {
        public Long taskId;
        public Long variableId;
    }

    @Data
    @NoArgsConstructor
    public static class ResendTaskOutputsV1 {
        public final List<ResendTaskOutputV1> resends = new ArrayList<>();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RequestLogFileV1 {
        public long requestedOn;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispatcherResponseV1 {
        public String processorCode;
        @Nullable
        public AssignedTaskV1 assignedTask;
        @Nullable
        public AssignedProcessorIdV1 assignedProcessorId;
        @Nullable
        public ReAssignProcessorIdV1 reAssignedProcessorId;
        @Nullable
        public ReportResultDeliveringV1 reportResultDelivering;
        @Nullable
        public ResendTaskOutputsV1 resendTaskOutputs;

        public DispatcherResponseV1(String processorCode) {
            this.processorCode = processorCode;
        }
    }

    public final List<DispatcherResponseV1> responses = new ArrayList<>();
    @Nullable
    public RequestLogFileV1 requestLogFile;

    public boolean success = true;
    public String msg;
}

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
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
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
 * Date: 8/29/2019
 * Time: 6:00 PM
 */
@Data
public class DispatcherCommParamsYaml implements BaseParams {

    public final int version=1;

    @Override
    public boolean checkIntegrity() {
        if (responses.isEmpty()) {
            throw new CheckIntegrityFailedException("responses.isEmpty()");
        }
        for (DispatcherResponse response : responses) {
            if (S.b(response.processorCode)) {
                throw new CheckIntegrityFailedException("(S.b(response.processorCode))");
            }
        }
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AssignedTask {
        public String params;
        public Long taskId;
        public Long execContextId;
        public EnumsApi.ExecContextState state;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignedProcessorId {
        public String assignedProcessorId;
        public String assignedSessionId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReAssignProcessorId {
        public String reAssignedProcessorId;
        public String sessionId;

        public ReAssignProcessorId(Long processorId, String sessionId) {
            this(Long.toString(processorId), sessionId);
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
    public static class ResendTaskOutput {
        public Long taskId;
        public Long variableId;
    }

    @Data
    @NoArgsConstructor
    public static class ResendTaskOutputs {
        public final List<ResendTaskOutput> resends = new ArrayList<>();
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
    public static class DispatcherResponse {
        public String processorCode;
        public @Nullable AssignedTask assignedTask;
        public @Nullable AssignedProcessorId assignedProcessorId;
        public @Nullable ReAssignProcessorId reAssignedProcessorId;
        public @Nullable ReportResultDelivering reportResultDelivering;
        public @Nullable ResendTaskOutputs resendTaskOutputs;

        public DispatcherResponse(String processorCode) {
            this.processorCode = processorCode;
        }
    }

    public final List<DispatcherResponse> responses = new ArrayList<>();
    public @Nullable RequestLogFile requestLogFile;

    public boolean success = true;
    public String msg;
}

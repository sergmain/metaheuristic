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

package ai.metaheuristic.ai.yaml.communication.processor;

import ai.metaheuristic.ai.Enums;
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
 * Communication file which is transferred from a Processor to Dispatcher
 *
 * @author Serge
 * Date: 11/13/2019
 * Time: 6:00 PM
 */
@Data
@NoArgsConstructor
public class ProcessorCommParamsYamlV1 implements BaseParams {

    public final int version=1;

    @Override
    public boolean checkIntegrity() {
        if (requests.isEmpty()) {
            throw new CheckIntegrityFailedException("requests.isEmpty()");
        }
        for (ProcessorRequestV1 request : requests) {
            if (S.b(request.processorCode)) {
                throw new CheckIntegrityFailedException("(S.b(request.processorCode))");
            }
        }
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessorCommContextV1 {
        @Nullable public String processorId;
        @Nullable public String sessionId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestProcessorIdV1 {
        public boolean keep = true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckForMissingOutputResourcesV1 {
        public boolean keep = true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RequestTaskV1 {
        @Nullable
        public Boolean newTask;
        public boolean acceptOnlySigned;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportTaskProcessingResultV1 {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SimpleTaskExecResult {
            public long taskId;
            // string form of FunctionApiData.FunctionExec
            public String result;
        }

        public List<SimpleTaskExecResult> results = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResendTaskOutputResourceResultV1 {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SimpleStatus {
            public Long taskId;
            public Long variableId;
            public Enums.ResendTaskOutputResourceStatus status;
        }

        public List<SimpleStatus> statuses;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessorRequestV1 {
        @Nullable
        public ProcessorCommContextV1 processorCommContext;
        @Nullable
        public RequestProcessorIdV1 requestProcessorId;
        @Nullable
        public RequestTaskV1 requestTask;
        @Nullable
        public ReportTaskProcessingResultV1 reportTaskProcessingResult;
        @Nullable
        public CheckForMissingOutputResourcesV1 checkForMissingOutputResources;
        @Nullable
        public ResendTaskOutputResourceResultV1 resendTaskOutputResourceResult;

        public String processorCode;

        public ProcessorRequestV1(String processorCode) {
            this.processorCode = processorCode;
        }
    }

    public final List<ProcessorRequestV1> requests = new ArrayList<>();
}

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
public class ProcessorCommParamsYamlV2 implements BaseParams {

    public final int version=2;

    @Override
    public boolean checkIntegrity() {
        if (requests.isEmpty()) {
            throw new CheckIntegrityFailedException("requests.isEmpty()");
        }
        for (ProcessorRequestV2 request : requests) {
            if (S.b(request.processorCode)) {
                throw new CheckIntegrityFailedException("(S.b(request.processorCode))");
            }
        }
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessorCommContextV2 {
        @Nullable public String processorId;
        @Nullable public String sessionId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestProcessorIdV2 {
        public boolean keep = true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckForMissingOutputResourcesV2 {
        public boolean keep = true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RequestTaskV2 {
        @Nullable
        public Boolean newTask;
        public boolean acceptOnlySigned;
        @Nullable
        public String taskIds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportTaskProcessingResultV2 {

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
    public static class ResendTaskOutputResourceResultV2 {

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
    public static class QuotasV2 {
        public int current;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessorRequestV2 {
        public @Nullable ProcessorCommContextV2 processorCommContext;
        public @Nullable RequestProcessorIdV2 requestProcessorId;
        public @Nullable RequestTaskV2 requestTask;
        public @Nullable ReportTaskProcessingResultV2 reportTaskProcessingResult;
        public @Nullable CheckForMissingOutputResourcesV2 checkForMissingOutputResources;
        public @Nullable ResendTaskOutputResourceResultV2 resendTaskOutputResourceResult;


        public String processorCode;

        public ProcessorRequestV2(String processorCode) {
            this.processorCode = processorCode;
        }
    }

    public final List<ProcessorRequestV2> requests = new ArrayList<>();
    public final QuotasV2 quotas = new QuotasV2();

}

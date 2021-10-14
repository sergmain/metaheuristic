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
 * Date: 8/29/2019
 * Time: 6:00 PM
 */
@Data
@NoArgsConstructor
public class ProcessorCommParamsYaml implements BaseParams {

    public final int version=1;

    @Override
    public boolean checkIntegrity() {
        if (requests.isEmpty()) {
            throw new CheckIntegrityFailedException("requests.isEmpty()");
        }
        for (ProcessorRequest request : requests) {
            if (S.b(request.processorCode)) {
                throw new CheckIntegrityFailedException("(S.b(request.processorCode))");
            }
        }
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessorCommContext {
        @Nullable public String processorId;
        @Nullable public String sessionId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestProcessorId {
        // TODO 2020-11-22 what is this field about?
        //  2021-04-09 it's just dummy field. do e need a dummy field or empty class is ok?
        public boolean keep = true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckForMissingOutputResources {
        public boolean keep = true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RequestTask {
        @Nullable
        public Boolean newTask;
        public boolean acceptOnlySigned;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportTaskProcessingResult {

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
    public static class ResendTaskOutputResourceResult {

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
    public static class ProcessorRequest {
        public @Nullable ProcessorCommContext processorCommContext;
        public @Nullable RequestProcessorId requestProcessorId;
        public @Nullable RequestTask requestTask;
        public @Nullable ReportTaskProcessingResult reportTaskProcessingResult;
        public @Nullable CheckForMissingOutputResources checkForMissingOutputResources;
        public @Nullable ResendTaskOutputResourceResult resendTaskOutputResourceResult;

        public String processorCode;

        public ProcessorRequest(String processorCode) {
            this.processorCode = processorCode;
        }
    }

    public final List<ProcessorRequest > requests = new ArrayList<>();

}

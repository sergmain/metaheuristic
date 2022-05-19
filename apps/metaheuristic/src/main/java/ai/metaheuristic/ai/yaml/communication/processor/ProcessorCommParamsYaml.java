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

    public final int version=3;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessorCommContext {
        @Nullable
        public Long processorId;
        @Nullable
        public String sessionId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestProcessorId {
        // TODO 2020-11-22 what is this field about?
        //  2021-04-09 it's just dummy field. do we need a dummy field or empty class is ok?
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
        public boolean newTask;
        public boolean acceptOnlySigned;
        @Nullable
        public String taskIds;
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
    public static class Core {
        public String code;
        @Nullable
        public Long coreId;
        @Nullable
        public RequestTask requestTask;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessorRequest {
        @Nullable
        public ProcessorCommContext processorCommContext;
        @Nullable
        public RequestProcessorId requestProcessorId;
        @Nullable
        public CheckForMissingOutputResources checkForMissingOutputResources;
        @Nullable
        public ResendTaskOutputResourceResult resendTaskOutputResourceResult;
        @Nullable
        public ReportTaskProcessingResult reportTaskProcessingResult;

        public final List<Core> cores = new ArrayList<>();

        public int currentQuota;
    }

    public void addReportTaskProcessingResult(@Nullable ReportTaskProcessingResult result) {
        if (result==null || result.results.isEmpty()) {
            return;
        }
        if (request.reportTaskProcessingResult==null) {
            request.reportTaskProcessingResult = new ReportTaskProcessingResult();
        }
        if (request.reportTaskProcessingResult.results==null) {
            request.reportTaskProcessingResult.results = new ArrayList<>();
        }
        request.reportTaskProcessingResult.results.addAll(result.results);
    }

    public void addResendTaskOutputResourceResult(List<ProcessorCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus> statuses) {
        if (statuses.isEmpty()) {
            return;
        }
        if (request.resendTaskOutputResourceResult==null) {
            request.resendTaskOutputResourceResult = new ResendTaskOutputResourceResult();
        }
        if (request.resendTaskOutputResourceResult.statuses==null) {
            request.resendTaskOutputResourceResult.statuses = new ArrayList<>();
        }
        request.resendTaskOutputResourceResult.statuses.addAll(statuses);
    }

    public final ProcessorRequest request = new ProcessorRequest();

}

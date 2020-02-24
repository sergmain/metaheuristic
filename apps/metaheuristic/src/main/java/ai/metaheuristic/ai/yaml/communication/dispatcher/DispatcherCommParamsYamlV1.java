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
 * Communication file which is transferred from a dispatcher to a Processor
 *
 * @author Serge
 * Date: 10/03/2019
 * Time: 6:00 PM
 */
@Data
public class DispatcherCommParamsYamlV1 implements BaseParams {

    public final int version=1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    public DispatcherCommContextV1 dispatcherCommContext;

    // always send info about functions
    public FunctionsV1 functions = new FunctionsV1();
    public AssignedTaskV1 assignedTask;
    public AssignedProcessorIdV1 assignedProcessorId;
    public ReAssignProcessorIdV1 reAssignedProcessorId;
    public ReportResultDeliveringV1 reportResultDelivering;
    public ExecContextStatusV1 execContextStatus;
    public ResendTaskOutputResourceV1 resendTaskOutputResource;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FunctionsV1 {
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
    public static class AssignedTaskV1 {
        public String params;
        public Long taskId;
        public Long execContextId;
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
    public static class ExecContextStatusV1 {

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
    public static class ResendTaskOutputResourceV1 {
        public List<Long> taskIds;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DispatcherCommContextV1 {
        public Long chunkSize;
        // Processor's version for communicating with dispatcher
        public Integer processorCommVersion;
    }

    public boolean success = true;
    public String msg;
}

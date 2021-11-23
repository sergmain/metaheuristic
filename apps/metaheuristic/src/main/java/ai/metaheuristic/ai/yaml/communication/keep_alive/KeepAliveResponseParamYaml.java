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
import java.util.List;

/**
 * @author Serge
 * Date: 11/21/2020
 * Time: 1:11 AM
 */
@Data
public class KeepAliveResponseParamYaml implements BaseParams {

    public final int version=1;

    @Override
    public boolean checkIntegrity() {
        return true;
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
    public static class Functions {
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Info {
            public String code;
            public EnumsApi.FunctionSourcing sourcing;
        }
        public final List<Info> infos = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class ExecContextStatus {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SimpleStatus {
            public Long id;
            public EnumsApi.ExecContextState state;
        }

        public final List<SimpleStatus> statuses = new ArrayList<>();

        public boolean isStarted(Long execContextId) {
            for (SimpleStatus status : statuses) {
                if (status.id.equals(execContextId)) {
                    return status.state== EnumsApi.ExecContextState.STARTED;
                }
            }
            return false;
        }

        @Nullable
        public SimpleStatus getStatus(Long execContextId) {
            return statuses.stream().filter(o->o.id.equals(execContextId)).findFirst().orElse(null);
        }
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
    public static class DispatcherResponse {
        public String processorCode;

        @Nullable
        public ReAssignedProcessorId reAssignedProcessorId;

        @Nullable
        public AssignedProcessorId assignedProcessorId;

        @Nullable
        public RequestLogFile requestLogFile;

        public DispatcherResponse(String processorCode) {
            this.processorCode = processorCode;
        }
    }

    public final List<DispatcherResponse> responses = new ArrayList<>();
    public final Functions functions = new Functions();
    public ExecContextStatus execContextStatus;
    public DispatcherInfo dispatcherInfo;

    public boolean success = true;
    public String msg;

}

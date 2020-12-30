/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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
public class KeepAliveResponseParamYamlV1 implements BaseParams {

    public final int version=1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReAssignedProcessorIdV1 {
        public String reAssignedProcessorId;
        public String sessionId;
    }

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
    @NoArgsConstructor
    public static class ExecContextStatusV1 {

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
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DispatcherInfoV1 {
        public Long chunkSize;
        // Processor's version for communicating with Dispatcher
        public Integer processorCommVersion;
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
    public static class AssignedProcessorIdV1 {
        public Long assignedProcessorId;
        public String assignedSessionId;
    }

    public final FunctionsV1 functions = new FunctionsV1();
    public ExecContextStatusV1 execContextStatus;
    public DispatcherInfoV1 dispatcherInfo;

    @Nullable
    public ReAssignedProcessorIdV1 reAssignedProcessorId;

    @Nullable
    public AssignedProcessorIdV1 assignedProcessorId;

    @Nullable
    public RequestLogFileV1 requestLogFile;

    public boolean success = true;
    public String msg;

}

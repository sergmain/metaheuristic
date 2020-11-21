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
    public static class ReAssignProcessorId {
        public String reAssignedProcessorId;
        public String sessionId;

        public ReAssignProcessorId(Long processorId, String sessionId) {
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
    @AllArgsConstructor
    public static class ExecContextStatus {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SimpleStatus {
            public Long execContextId;
            public EnumsApi.ExecContextState state;
        }

        public List<SimpleStatus> statuses;

        public boolean isStarted(Long execContextId) {
            for (SimpleStatus status : statuses) {
                if (status.execContextId.equals(execContextId)) {
                    return status.state== EnumsApi.ExecContextState.STARTED;
                }
            }
            return false;
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

    public final Functions functions = new Functions();
    public ExecContextStatus execContextStatus;
    public DispatcherInfo dispatcherInfo;
    public ReAssignProcessorId reAssignProcessorId;

    @Nullable
    public RequestLogFile requestLogFile;

    public boolean success = true;
    public String msg;

}

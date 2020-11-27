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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.commons.yaml.env.EnvYaml;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 11/21/2020
 * Time: 1:03 AM
 */
@Data
public class KeepAliveRequestParamYaml implements BaseParams {

    public final int version=1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportProcessor {
        public EnvYaml env;
        public GitSourcingService.GitStatusInfo gitStatusInfo;
        public String schedule;
        public String sessionId;

        // TODO 2019-05-28, a multi-time-zoned deployment isn't supported right now
        // it'll work but in some cases behaviour can be different
        // need to change it to UTC, Coordinated Universal Time
        public long sessionCreatedOn;
        public String ip;
        public String host;

        // contains text of error which can occur while preparing a processor status
        public List<String> errors = null;
        public boolean logDownloadable;
        public int taskParamsVersion;

        public EnumsApi.OS os;

        @Nullable
        public String currDir;

        public void addError(String error) {
            if (errors==null) {
                errors = new ArrayList<>();
            }
            errors.add(error);
        }
    }

    @Data
    public static class FunctionDownloadStatuses {
        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class Status {
            public String code;
            public Enums.FunctionState state;
        }

        public List<Status> statuses = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestProcessorId {
        // TODO 2020-11-22 do we actually need this field?
        public boolean keep = true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessorCommContext {
        @Nullable public Long processorId;
        @Nullable public String sessionId;

        public ProcessorCommContext(String processorId, @Nullable String sessionId) {
            this.processorId = Long.valueOf(processorId);
            this.sessionId = sessionId;
        }
    }

    public ReportProcessor processor;
    public final FunctionDownloadStatuses functions = new FunctionDownloadStatuses();

    @Nullable
    public RequestProcessorId requestProcessorId;
    public ProcessorCommContext processorCommContext;

    @Nullable
    public String taskIds;

}
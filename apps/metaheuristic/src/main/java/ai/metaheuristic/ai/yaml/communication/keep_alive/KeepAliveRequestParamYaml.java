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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Serge
 * Date: 11/21/2020
 * Time: 1:03 AM
 */
@Data
public class KeepAliveRequestParamYaml implements BaseParams {

    public final int version=2;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode( of={"code","path"})
    public static class DiskStorage {
        public String code;
        public String path;
    }

    // event though at processor side a quatas is placed in env.yaml above all processors level
    // here it'll be placed at concrete processor
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
//    @EqualsAndHashCode( of={"tag","amount"})
    public static class Quota {
        public String tag;
        public int amount;
        // processor can disable specific tag. i.e. on scheduler basis
        public boolean disabled;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Quotas {
        public List<Quota> values = new ArrayList<>();
        public int limit;
        public int defaultValue;
        public boolean disabled;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Env {
        public final Map<String, String> mirrors = new ConcurrentHashMap<>();
        public final Map<String, String> envs = new ConcurrentHashMap<>();
        public final List<DiskStorage> disk = new ArrayList<>();

        // this field is specific for concrete processorCode
        @Nullable
        public String tags;

        public final Quotas quotas = new Quotas();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportProcessor {
        public Env env;
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
//    @AllArgsConstructor
    public static class RequestProcessorId {
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessorRequest {
        public ReportProcessor processor;

        @Nullable
        public RequestProcessorId requestProcessorId;
        public ProcessorCommContext processorCommContext;

        @Nullable
        private String taskIds = null;

        @Nullable
        public String getTaskIds() {
            return taskIds;
        }

        @SuppressWarnings("MethodMayBeStatic")
        public void setTaskIds(@Nullable String taskIds) {
            throw new IllegalStateException("taskIds isn't used any more");
        }

        public String processorCode;

        public ProcessorRequest(String processorCode) {
            this.processorCode = processorCode;
        }
    }

    public final FunctionDownloadStatuses functions = new FunctionDownloadStatuses();

    public final List<ProcessorRequest> requests = new ArrayList<>();

}

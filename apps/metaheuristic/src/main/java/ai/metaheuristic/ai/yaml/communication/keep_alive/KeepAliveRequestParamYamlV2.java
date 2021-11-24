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
public class KeepAliveRequestParamYamlV2 implements BaseParams {

    public final int version=2;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode( of={"code","path"})
    public static class DiskStorageV2 {
        public String code;
        public String path;
    }

    // event though at processor side a quatas is placed in env.yaml above all processors level
    // here it'll be placed at concrete processor
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QuotaV2 {
        public String tag;
        public int amount;
        // processor can disable specific tag. i.e. on scheduler basis
        public boolean disabled;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QuotasV2 {
        public List<QuotaV2> values = new ArrayList<>();
        public int limit;
        public int defaultValue;
        public boolean disabled;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnvV2 {
        public final Map<String, String> mirrors = new ConcurrentHashMap<>();
        public final Map<String, String> envs = new ConcurrentHashMap<>();
        public final List<DiskStorageV2> disk = new ArrayList<>();

        @Nullable
        public String tags;

        public final QuotasV2 quotas = new QuotasV2();

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportProcessorV2 {
        public EnvV2 env;
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
    public static class FunctionDownloadStatusesV2 {
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
    public static class RequestProcessorIdV2 {
        public String processorCode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessorCommContextV2 {
        @Nullable public Long processorId;
        @Nullable public String sessionId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessorRequestV2 {
        public ReportProcessorV2 processor;

        @Nullable
        public RequestProcessorIdV2 requestProcessorId;
        public ProcessorCommContextV2 processorCommContext;

        @Nullable
        public String taskIds;

        public String processorCode;

        public ProcessorRequestV2(String processorCode) {
            this.processorCode = processorCode;
        }
    }

    public final FunctionDownloadStatusesV2 functions = new FunctionDownloadStatusesV2();

    public final List<ProcessorRequestV2> requests = new ArrayList<>();
}

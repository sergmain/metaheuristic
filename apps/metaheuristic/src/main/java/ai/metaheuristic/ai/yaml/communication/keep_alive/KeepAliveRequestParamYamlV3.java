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

import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 11/21/2020
 * Time: 1:03 AM
 */
@Data
public class KeepAliveRequestParamYamlV3 implements BaseParams {

    public final int version=3;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode( of={"code","path"})
    public static class DiskStorageV3 {
        public String code;
        public String path;
    }

    // event though at core side a quatas is placed in env.yaml above all cores level
    // here it'll be placed at concrete core
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
//    @EqualsAndHashCode( of={"tag","amount"})
    public static class QuotaV3 {
        public String tag;
        public int amount;
        // core can disable specific tag. i.e. on scheduler basis
        public boolean disabled;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QuotasV3 {
        public List<QuotaV3> values = new ArrayList<>();
        public int limit;
        public int defaultValue;
        public boolean disabled;

        public void init(QuotasV3 v3) {
            this.values.addAll(v3.values);
            this.limit = v3.limit;
            this.defaultValue = v3.defaultValue;
            this.disabled = v3.disabled;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoreV3 {
        public String coreDir;
        @Nullable
        public Long coreId;

        public String coreCode;

        @Nullable
        public String tags;

        public CoreV3(String coreCode) {
            this.coreCode = coreCode;
        }
    }

    @Data
    @NoArgsConstructor
    public static class EnvV3 {
        public final Map<String, String> mirrors = new HashMap<>();
        public final Map<String, String> envs = new HashMap<>();
        public final List<DiskStorageV3> disk = new ArrayList<>();
        public QuotasV3 quotas = new QuotasV3();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    // ReportProcessor
    public static class ProcessorStatusV3 {
        public EnvV3 env;
        @Nullable
        public GitSourcingService.GitStatusInfo gitStatusInfo;
        public String schedule;
        public String ip;
        public String host;

        public boolean logDownloadable;
        public int taskParamsVersion;

        public EnumsApi.OS os;
        public String currDir;

        // contains text of error which can occur while preparing a processor status
        @Nullable
        public List<String> errors = null;

        public void addError(String error) {
            if (errors==null) {
                errors = new ArrayList<>();
            }
            errors.add(error);
        }
    }

    @Data
    public static class FunctionDownloadStatusesV3 {
        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class StatusV3 {
            public String code;
            public EnumsApi.FunctionState state;
        }

        public List<StatusV3> statuses = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessorCommContextV3 {
        public Long processorId;
        public String sessionId;
        // TODO 2019-05-28, a multi-time-zoned deployment isn't supported right now
        // it'll work but in some cases behaviour can be different
        // need to change it to UTC, Coordinated Universal Time
        public long sessionCreatedOn;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessorV3 {
        public ProcessorStatusV3 status;

        // if not null it means we need a new processorId
        @Nullable
        public ProcessorCommContextV3 processorCommContext;

        public String processorCode;

        public ProcessorV3(String processorCode) {
            this.processorCode = processorCode;
        }
    }

    public final ProcessorV3 processor = new ProcessorV3();
    public final List<CoreV3> cores = new ArrayList<>();
    public final FunctionDownloadStatusesV3 functions = new FunctionDownloadStatusesV3();

}
